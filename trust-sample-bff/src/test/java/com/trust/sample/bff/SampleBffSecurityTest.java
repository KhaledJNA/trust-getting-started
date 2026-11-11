package com.trust.sample.bff;

import com.trust.restclient.error.ServiceClientResponseException;
import com.trust.sample.bff.relay.ApiClient;
import com.trust.security.client.test.WithTrustOidcUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.testSecurityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fast tests of this BFF's security, with no Keycloak:
 * {@code @WithTrustOidcUser} builds the OIDC session from a claim set,
 * through this application's real authorities mapping
 * (trust-oauth2-client-test), and the APIs behind it are mocked.
 */
// Placeholder secrets: the real file is only written by a realm install.
@SpringBootTest(properties = "TRUST_SAMPLE_SECRETS=src/test/resources/test-secrets.env")
@AutoConfigureMockMvc
@Import(SampleBffSecurityTest.NoDiscovery.class)
class SampleBffSecurityTest {

    private static final String REGISTRATION = "trust-sample";

    /** The real registration discovers Keycloak at startup; this one does not. */
    @TestConfiguration
    static class NoDiscovery {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId(REGISTRATION)
                    .clientId("trust-sample-bff")
                    .clientSecret("test")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .authorizationUri("https://idp.example.test/auth")
                    .tokenUri("https://idp.example.test/token")
                    .jwkSetUri("https://idp.example.test/certs")
                    .scope("openid")
                    .build());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiClient api;

    @Test
    void thePageAndTheSessionProbeArePublic() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/api/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void withoutASessionTheBrowserIsSentToLogin() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/" + REGISTRATION));
    }

    @Test
    @WithTrustOidcUser(value = "user.json", registrationId = REGISTRATION)
    void theSessionShowsTheMappedRealmRoles() throws Exception {
        mockMvc.perform(get("/api/session").with(testSecurityContext()))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user").value("sample.user"))
                .andExpect(jsonPath("$.authorities", hasItem("ROLE_SAMPLE_USER")));
    }

    @Test
    @WithTrustOidcUser(value = "user.json", registrationId = REGISTRATION)
    void theApiAnswerIsRelayed() throws Exception {
        given(api.me()).willReturn(Map.of("user", "sample.user"));

        mockMvc.perform(get("/api/me").with(testSecurityContext()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value("sample.user"));
    }

    @Test
    @WithTrustOidcUser(value = "user.json", registrationId = REGISTRATION)
    void anApiDenialIsPassedBackAsForbidden() throws Exception {
        given(api.writeDocument("2")).willThrow(apiError(403, null));

        mockMvc.perform(put("/api/documents/2")).andExpect(status().isForbidden());
    }

    @Test
    @WithTrustOidcUser(value = "user.json", registrationId = REGISTRATION)
    void anApiStepUpChallengeIsPassedBack() throws Exception {
        String challenge = "Bearer error=\"insufficient_user_authentication\", acr_values=\"strong\"";
        given(api.sensitive()).willThrow(apiError(401, challenge));

        mockMvc.perform(get("/api/sensitive").with(testSecurityContext()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, challenge));
    }

    @Test
    @WithTrustOidcUser(value = "user.json", registrationId = REGISTRATION)
    void anyOtherApiFailureIsABadGateway() throws Exception {
        given(api.me()).willThrow(apiError(500, null));

        mockMvc.perform(get("/api/me").with(testSecurityContext())).andExpect(status().isBadGateway());
    }

    @Test
    @WithTrustOidcUser(value = "user.json", registrationId = REGISTRATION)
    void aBasicSessionGetsAStepUpChallengeFromTheBff() throws Exception {
        mockMvc.perform(get("/api/local/strong").with(testSecurityContext()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("acr_values=\"strong\"")));
    }

    @Test
    @WithTrustOidcUser(value = "user.json", registrationId = REGISTRATION, acr = "strong")
    void aStrongSessionIsGranted() throws Exception {
        mockMvc.perform(get("/api/local/strong").with(testSecurityContext())).andExpect(status().isOk());
    }

    @Test
    @WithTrustOidcUser(value = "user.json", registrationId = REGISTRATION)
    void logoutNeedsTheCsrfToken() throws Exception {
        mockMvc.perform(post("/logout").with(testSecurityContext())).andExpect(status().isForbidden());
        mockMvc.perform(post("/logout").with(testSecurityContext()).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    private static ServiceClientResponseException apiError(int status, String challenge) {
        HttpHeaders headers = new HttpHeaders();
        if (challenge != null) {
            headers.set(HttpHeaders.WWW_AUTHENTICATE, challenge);
        }
        return new ServiceClientResponseException("status " + status, HttpStatusCode.valueOf(status),
                "status " + status, headers, new byte[0], StandardCharsets.UTF_8, null);
    }

    private static org.springframework.test.web.servlet.RequestBuilder put(String path) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                .with(testSecurityContext()).with(csrf());
    }
}
