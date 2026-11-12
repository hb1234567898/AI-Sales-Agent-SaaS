package com.yourcompany.salesagent.auth.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
    "spring.flyway.enabled=false",
    "app.demo.seed-enabled=false",
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unused",
    "spring.datasource.hikari.initialization-fail-timeout=-1",
    "management.health.db.enabled=false"
})
class PublicHealthEndpointTests {
    @Autowired WebApplicationContext context;

    @Test void actuatorHealthUsesThePublicPrefixAndHidesDetails() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        mvc.perform(get("/api/public/health"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist()).andExpect(jsonPath("$.details").doesNotExist());
        mvc.perform(get("/api/public/info")).andExpect(status().isNotFound());
        mvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized());
    }
}
