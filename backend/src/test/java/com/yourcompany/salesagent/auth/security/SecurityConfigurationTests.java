package com.yourcompany.salesagent.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class SecurityConfigurationTests {

	@Test
	void sessionFilterIsOnlyRegisteredInsideTheSpringSecurityChain() {
		var configuration = new SecurityConfiguration();
		var registration = configuration.sessionAuthenticationFilterRegistration(
				mock(SessionAuthenticationFilter.class));

		assertThat(registration.isEnabled()).isFalse();
	}
}
