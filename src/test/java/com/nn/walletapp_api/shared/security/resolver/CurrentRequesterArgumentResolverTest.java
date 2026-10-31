package com.nn.walletapp_api.shared.security.resolver;

import com.nn.walletapp_api.shared.security.annotation.CurrentRequester;
import com.nn.walletapp_api.shared.security.domain.AccessLevel;
import com.nn.walletapp_api.shared.security.domain.RequesterContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class CurrentRequesterArgumentResolverTest {

    private final CurrentRequesterArgumentResolver resolver = new CurrentRequesterArgumentResolver();

    @Mock
    private MethodParameter parameter;

    @Mock
    private ModelAndViewContainer mavContainer;

    @Mock
    private NativeWebRequest webRequest;

    @Mock
    private WebDataBinderFactory binderFactory;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should resolve RequesterContext with STANDARD access level for regular user")
    void shouldResolveStandardRequesterContext() {
        // given
        UUID userId = UUID.randomUUID();
        Jwt jwt = createJwt(userId.toString());
        JwtAuthenticationToken auth = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        setAuthentication(auth);

        // when
        Object result = resolver.resolveArgument(parameter, mavContainer, webRequest, binderFactory);

        // then
        assertThat(result).isInstanceOf(RequesterContext.class);
        RequesterContext context = (RequesterContext) result;
        assertThat(context.userId()).isEqualTo(userId);
        assertThat(context.accessLevel()).isEqualTo(AccessLevel.STANDARD);
        assertThat(context.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("Should resolve RequesterContext with ADMIN access level when ROLE_ADMIN present")
    void shouldResolveAdminRequesterContext() {
        // given
        UUID adminId = UUID.randomUUID();
        Jwt jwt = createJwt(adminId.toString());
        JwtAuthenticationToken auth = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
        );
        setAuthentication(auth);

        // when
        Object result = resolver.resolveArgument(parameter, mavContainer, webRequest, binderFactory);

        // then
        assertThat(result).isInstanceOf(RequesterContext.class);
        RequesterContext context = (RequesterContext) result;
        assertThat(context.userId()).isEqualTo(adminId);
        assertThat(context.accessLevel()).isEqualTo(AccessLevel.ADMIN);
        assertThat(context.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("Should throw AuthenticationCredentialsNotFoundException when authentication is missing")
    void shouldThrowWhenAuthenticationMissing() {
        // given
        SecurityContextHolder.clearContext();

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(parameter, mavContainer, webRequest, binderFactory))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageContaining("Valid JWT authentication token required");
    }

    @Test
    @DisplayName("Should throw AuthenticationCredentialsNotFoundException when authentication is not JwtAuthenticationToken")
    void shouldThrowWhenNotJwtToken() {
        // given
        UsernamePasswordAuthenticationToken nonJwtAuth =
                new UsernamePasswordAuthenticationToken("user", "pass");
        setAuthentication(nonJwtAuth);

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(parameter, mavContainer, webRequest, binderFactory))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageContaining("Valid JWT authentication token required");
    }

    @Test
    @DisplayName("Should throw AuthenticationCredentialsNotFoundException when sub claim is not valid UUID")
    void shouldThrowWhenSubIsNotValidUuid() {
        // given
        Jwt jwt = createJwt("invalid-non-uuid-subject");
        JwtAuthenticationToken auth = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        setAuthentication(auth);

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(parameter, mavContainer, webRequest, binderFactory))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageContaining("not a valid UUID");
    }

    @Test
    @DisplayName("Should support parameter annotated with @CurrentRequester and RequesterContext type")
    void shouldSupportValidParameter() {
        // given
        given(parameter.hasParameterAnnotation(CurrentRequester.class)).willReturn(true);
        given(parameter.getParameterType()).willReturn((Class) RequesterContext.class);

        // when
        boolean supported = resolver.supportsParameter(parameter);

        // then
        assertThat(supported).isTrue();
    }

    private Jwt createJwt(String subject) {
        return Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private void setAuthentication(Authentication auth) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }
}
