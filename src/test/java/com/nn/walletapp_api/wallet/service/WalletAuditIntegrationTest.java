package com.nn.walletapp_api.wallet.service;

import com.nn.walletapp_api.shared.security.domain.AccessLevel;
import com.nn.walletapp_api.shared.security.domain.RequesterContext;
import com.nn.walletapp_api.support.BaseIntegrationTest;
import com.nn.walletapp_api.support.SecurityTestUtils;
import com.nn.walletapp_api.transaction.repository.TransactionRepository;
import com.nn.walletapp_api.wallet.api.request.WalletStatusUpdateRequest;
import com.nn.walletapp_api.wallet.api.response.WalletAuditResponse;
import com.nn.walletapp_api.wallet.api.response.WalletResponse;
import com.nn.walletapp_api.wallet.domain.Wallet;
import com.nn.walletapp_api.wallet.domain.WalletStatus;
import com.nn.walletapp_api.wallet.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class WalletAuditIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private Clock clock;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should record Envers audit trail across provisioning, balance mutation and admin lock with authorId")
    void shouldRecordAuditTrailAcrossBalanceAndStatusMutations() {
        // given
        UUID userSub = UUID.randomUUID();
        UUID adminSub = UUID.randomUUID();
        RequesterContext userRequester = new RequesterContext(userSub, AccessLevel.STANDARD);

        // 1. User provisions wallet (Revision 1 - ADD)
        authenticateAs(userSub, "ROLE_USER");
        WalletResponse provisioned = walletService.getOrProvisionMyWallet(userRequester);
        UUID walletId = provisioned.id();

        // 2. User credits wallet balance within a new transaction (Revision 2 - MOD)
        transactionTemplate.executeWithoutResult(status -> {
            Wallet wallet = walletRepository.findById(walletId).orElseThrow();
            wallet.credit(new BigDecimal("250.5000"));
        });

        // 3. Admin locks the wallet (Revision 3 - MOD)
        authenticateAs(adminSub, "ROLE_ADMIN");
        walletService.updateWalletStatus(walletId, new WalletStatusUpdateRequest(WalletStatus.LOCKED));

        // when
        List<WalletAuditResponse> auditHistory = walletService.getWalletAuditHistory(walletId);

        // then
        assertThat(auditHistory).hasSize(3);

        WalletAuditResponse rev1 = auditHistory.get(0);
        assertThat(rev1.revisionType()).isEqualTo("ADD");
        assertThat(rev1.authorId()).isEqualTo(userSub);
        assertThat(rev1.walletId()).isEqualTo(walletId);
        assertThat(rev1.ownerSub()).isEqualTo(userSub);
        assertThat(rev1.balance()).isEqualByComparingTo(new BigDecimal("0.0000"));
        assertThat(rev1.status()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(rev1.revisionTimestamp()).isNotNull();

        WalletAuditResponse rev2 = auditHistory.get(1);
        assertThat(rev2.revisionNumber()).isGreaterThan(rev1.revisionNumber());
        assertThat(rev2.revisionType()).isEqualTo("MOD");
        assertThat(rev2.authorId()).isEqualTo(userSub);
        assertThat(rev2.balance()).isEqualByComparingTo(new BigDecimal("250.5000"));
        assertThat(rev2.status()).isEqualTo(WalletStatus.ACTIVE);

        WalletAuditResponse rev3 = auditHistory.get(2);
        assertThat(rev3.revisionNumber()).isGreaterThan(rev2.revisionNumber());
        assertThat(rev3.revisionType()).isEqualTo("MOD");
        assertThat(rev3.authorId()).isEqualTo(adminSub);
        assertThat(rev3.balance()).isEqualByComparingTo(new BigDecimal("250.5000"));
        assertThat(rev3.status()).isEqualTo(WalletStatus.LOCKED);
    }

    private void authenticateAs(UUID subject, String role) {
        Jwt jwt = SecurityTestUtils.createJwt(subject, List.of(role), clock);
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority(role))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}