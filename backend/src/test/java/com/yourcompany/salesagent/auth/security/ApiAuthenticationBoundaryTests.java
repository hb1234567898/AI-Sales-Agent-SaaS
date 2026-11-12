package com.yourcompany.salesagent.auth.security;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import com.yourcompany.salesagent.auth.application.AuthService;
import com.yourcompany.salesagent.auth.application.AuthTokenPair;
import com.yourcompany.salesagent.auth.api.AuthController;
import com.yourcompany.salesagent.auth.api.PublicAuthController;
import com.yourcompany.salesagent.auth.api.PasswordPublicKeyResponse;
import com.yourcompany.salesagent.customer.api.CustomerController;
import com.yourcompany.salesagent.customer.application.CustomerService;

@SpringJUnitConfig(ApiAuthenticationBoundaryTests.TestConfig.class)
@WebAppConfiguration
@TestPropertySource(properties = "app.security.cors.allowed-origin-patterns=http://localhost:5173")
class ApiAuthenticationBoundaryTests {
    @Autowired WebApplicationContext context;
    @Autowired AuthService authService;
    @Autowired CustomerService customerService;
    @Autowired PasswordTransportService passwords;
    MockMvc mvc;
    static final String CUSTOMER = "30000000-0000-0000-0000-000000000001";

    @BeforeEach void setup() {
        reset(authService, customerService, passwords);
        when(authService.resolveAccessToken("invalid.jwt")).thenReturn(Optional.empty());
        when(authService.resolveAccessToken("sales.jwt")).thenReturn(Optional.of(principal("SALES")));
        when(authService.resolveAccessToken("admin.jwt")).thenReturn(Optional.of(principal("ADMIN")));
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    static Stream<Arguments> protectedRequests() {
        return Stream.of(
            Arguments.of("GET", "/api/v1/customers"),
            Arguments.of("GET", "/api/v1/customers/" + CUSTOMER),
            Arguments.of("GET", "/api/v1/customers/metrics"),
            Arguments.of("GET", "/api/v1/customers/owners"),
            Arguments.of("GET", "/api/v1/customers/" + CUSTOMER + "/interactions"),
            Arguments.of("GET", "/api/v1/customers/" + CUSTOMER + "/interactions/analyses"),
            Arguments.of("GET", "/api/v1/leads"),
            Arguments.of("GET", "/api/v1/files?customerId=" + CUSTOMER),
            Arguments.of("GET", "/api/v1/ai/model"),
            Arguments.of("GET", "/api/v1/email/settings"),
            Arguments.of("GET", "/api/v1/opportunities"),
            Arguments.of("GET", "/api/v1/follow-ups"),
            Arguments.of("GET", "/api/v1/follow-ups/reminders"),
            Arguments.of("GET", "/api/v1/mail/messages"),
            Arguments.of("GET", "/api/v1/commercial/products"),
            Arguments.of("GET", "/api/v1/commercial/quotes"),
            Arguments.of("GET", "/api/v1/commercial/contracts"),
            Arguments.of("GET", "/api/v1/commercial/receivables"),
            Arguments.of("GET", "/api/v1/knowledge/documents"),
            Arguments.of("GET", "/api/v1/mcp/conversations"),
            Arguments.of("GET", "/api/v1/approvals"),
            Arguments.of("GET", "/api/v1/agent-runs"),
            Arguments.of("GET", "/api/v1/admin/members"),
            Arguments.of("GET", "/api/v1/audit-events"),
            Arguments.of("GET", "/api/v1/auth/session"),
            Arguments.of("POST", "/api/v1/auth/logout"),
            Arguments.of("GET", "/api/v1/system/health"),
            Arguments.of("GET", "/actuator/info"),
            Arguments.of("GET", "/api/v1/future-feature"),
            Arguments.of("HEAD", "/api/v1/customers"),
            Arguments.of("POST", "/api/v1/customers"),
            Arguments.of("PUT", "/api/v1/customers/" + CUSTOMER),
            Arguments.of("DELETE", "/api/v1/customers/" + CUSTOMER + "?version=0"),
            Arguments.of("GET", "/api/v1/auth/login"),
            Arguments.of("POST", "/api/v1/auth/password-key"),
            Arguments.of("POST", "/api/v1/auth/login"),
            Arguments.of("POST", "/api/v1/auth/refresh"),
            Arguments.of("GET", "/api/v1/auth/password-key"),
            Arguments.of("GET", "/actuator/health"),
            Arguments.of("GET", "/api/publicity/data"),
            Arguments.of("GET", "/api/v1/public/data"));
    }

    @ParameterizedTest @MethodSource("protectedRequests")
    void businessRequestsRequireLogin(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().isUnauthorized());
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer invalid.jwt"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(customerService);
    }

    static Stream<Arguments> publicRequests() {
        return Stream.of(Arguments.of("GET", "/api/public/future-feature"),
            Arguments.of("POST", "/api/public/future-feature"),
            Arguments.of("GET", "/api/public/health"));
    }

    @ParameterizedTest @MethodSource("publicRequests")
    void publicPrefixReachesHandlerWithoutLogin(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)).andExpect(status().isOk());
    }

    @Test void publicAuthEndpointsUseRealControllerWithoutAccessToken() throws Exception {
        when(passwords.publicKey()).thenReturn(new PasswordPublicKeyResponse(false, null, "RSA-OAEP-256", null));
        when(passwords.resolvePassword(any())).thenReturn("password");
        var pair = new AuthTokenPair("new-access", Instant.parse("2099-01-01T00:00:00Z"), "new-refresh", Instant.parse("2099-02-01T00:00:00Z"), principal("SALES"));
        when(authService.login(eq("test@example.com"), eq("password"), eq(false), any(), any())).thenReturn(pair);
        when(authService.refresh("refresh-token")).thenReturn(pair);
        mvc.perform(get("/api/public/auth/password-key")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/public/auth/login").contentType("application/json")
                .content("{\"email\":\"test@example.com\",\"password\":\"password\",\"rememberMe\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").value("new-access"));
        mvc.perform(post("/api/public/auth/refresh").contentType("application/json").content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").value("new-refresh"));
    }

    @Test void publicRefreshStillValidatesItsRequiredCredential() throws Exception {
        mvc.perform(post("/api/public/auth/refresh").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verify(authService, never()).refresh(any());
    }

    @Test void validBearerTokenCanReadCustomers() throws Exception {
        mvc.perform(get("/api/v1/customers/" + CUSTOMER).header("Authorization", "Bearer sales.jwt"))
                .andExpect(status().isOk());
        verify(customerService).findCustomer(UUID.fromString(CUSTOMER));
    }

    @Test void fallbackTokenHeaderStillAuthenticates() throws Exception {
        mvc.perform(get("/api/v1/customers/" + CUSTOMER).header("X-Sales-Agent-Access-Token", "sales.jwt"))
                .andExpect(status().isOk());
        verify(customerService).findCustomer(UUID.fromString(CUSTOMER));
    }

    @Test void loginDoesNotGrantSalesAdministratorDeletionPermission() throws Exception {
        mvc.perform(delete("/api/v1/customers/" + CUSTOMER + "?version=0").header("Authorization", "Bearer sales.jwt"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(customerService);
        mvc.perform(delete("/api/v1/customers/" + CUSTOMER + "?version=0").header("Authorization", "Bearer admin.jwt"))
                .andExpect(status().isNoContent());
        verify(customerService).deleteCustomer(principal("ADMIN"), UUID.fromString(CUSTOMER), 0);
    }

    @Test void corsPreflightDoesNotExposeBusinessData() throws Exception {
        mvc.perform(options("/api/v1/customers").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(content().string(""));
        verifyNoInteractions(customerService);
    }

    static AuthPrincipal principal(String role) {
        var id = UUID.fromString(CUSTOMER);
        return new AuthPrincipal(id, id, id, id, "test@example.com", "Test", "Team", role, Instant.parse("2099-01-01T00:00:00Z"));
    }

    @TestConfiguration @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfiguration.class, CustomerController.class, AuthController.class, PublicAuthController.class, BoundaryProbe.class})
    static class TestConfig {
        @Bean PasswordTransportService passwords() { return mock(PasswordTransportService.class); }
        @Bean AuthService authService() { return mock(AuthService.class); }
        @Bean CustomerService customerService() { return mock(CustomerService.class); }
        @Bean BearerTokenAuthenticationFilter bearerFilter(AuthService service) { return new BearerTokenAuthenticationFilter(service); }
    }

    // A fallback handler lets these tests exercise the production filter chain without databases or model services.
    @TestComponent @RestController
    static class BoundaryProbe {
        @RequestMapping("/**") Map<String, String> reached() { return Map.of("handler", "reached"); }
    }
}
