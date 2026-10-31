package com.nn.walletapp_api;

import com.nn.walletapp_api.support.BaseIntegrationTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
class WalletappApiApplicationTests extends BaseIntegrationTest {

	@Test
	@DisplayName("Should successfully bootstrap application context with Testcontainers")
	void contextLoads() {
	}

}
