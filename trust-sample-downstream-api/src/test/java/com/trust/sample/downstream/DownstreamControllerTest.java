package com.trust.sample.downstream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.opaqueToken;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fast tests, no Keycloak: {@code opaqueToken()} stands in for an
 * introspected token (introspection itself is certified against a real
 * Keycloak by trust-security-integration-tests).
 */
// Placeholder secrets: the real file is only written by a realm install.
@SpringBootTest(properties = "TRUST_SAMPLE_SECRETS=src/test/resources/test-secrets.env")
@AutoConfigureMockMvc
class DownstreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicPingNeedsNoToken() throws Exception {
        mockMvc.perform(get("/public/ping")).andExpect(status().isOk());
    }

    @Test
    void dataWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/data")).andExpect(status().isUnauthorized());
    }

    @Test
    void dataForASampleUserShowsWhichClientTheTokenWasIssuedTo() throws Exception {
        mockMvc.perform(get("/data").with(opaqueToken()
                        .attributes(attributes -> attributes.putAll(Map.of(
                                "sub", "user-1",
                                "preferred_username", "sample.user",
                                "client_id", "trust-sample-api")))
                        .authorities(new SimpleGrantedAuthority("ROLE_SAMPLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuedTo").value("trust-sample-api"));
    }

    @Test
    void dataWithoutTheRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/data").with(opaqueToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemStatusNeedsTheSystemScope() throws Exception {
        mockMvc.perform(get("/system/status").with(opaqueToken()
                        .authorities(new SimpleGrantedAuthority("ROLE_SAMPLE_USER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/system/status").with(opaqueToken()
                        .attributes(attributes -> attributes.put("client_id", "trust-sample-api"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_sample.system"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calledBy").value("trust-sample-api"));
    }
}
