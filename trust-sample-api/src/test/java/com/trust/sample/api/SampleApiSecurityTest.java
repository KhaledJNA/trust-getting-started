package com.trust.sample.api;

import com.trust.security.core.authorization.AuthorizationRequest;
import com.trust.security.resourceserver.test.WithTrustJwt;
import com.trust.security.test.authorization.TestAuthorizationDecisionClient;
import com.trust.security.test.authorization.TrustAuthorizationTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.testSecurityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fast tests of this application's security, with no Keycloak:
 * <ul>
 *   <li>{@code @WithTrustJwt("user.json")} builds a JWT from a claim set and
 *   runs it through this application's real claims → authorities mapping
 *   (trust-resource-server-test);</li>
 *   <li>{@link TestAuthorizationDecisionClient} replaces only Keycloak's
 *   UMA decision; the {@code @RequiresPermission} chain and the
 *   {@code PermissionContextProvider} are the real ones (trust-security-test).</li>
 * </ul>
 * The same scenarios against a real Keycloak: see the README's demo walkthrough.
 */
// Placeholder secrets: the real file is only written by a realm install.
@SpringBootTest(properties = "TRUST_SAMPLE_SECRETS=src/test/resources/test-secrets.env")
@AutoConfigureMockMvc
@Import(TrustAuthorizationTestConfiguration.class)
class SampleApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestAuthorizationDecisionClient decisions;

    @Test
    void publicPingNeedsNoToken() throws Exception {
        mockMvc.perform(get("/public/ping")).andExpect(status().isOk());
    }

    @Test
    void noTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithTrustJwt("user.json")
    void realmRoleIsMapped() throws Exception {
        mockMvc.perform(get("/me").with(testSecurityContext()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value("sample.user"));
    }

    @Test
    @WithTrustJwt("denied.json")
    void withoutTheRealmRoleAccessIsForbidden() throws Exception {
        mockMvc.perform(get("/me").with(testSecurityContext())).andExpect(status().isForbidden());
    }

    @Test
    @WithTrustJwt("user.json")
    void clientRoleOfThisApiIsMapped() throws Exception {
        mockMvc.perform(get("/reader").with(testSecurityContext())).andExpect(status().isOk());
    }

    @Test
    @WithTrustJwt("manager.json")
    void withoutTheClientRoleAccessIsForbidden() throws Exception {
        mockMvc.perform(get("/reader").with(testSecurityContext())).andExpect(status().isForbidden());
    }

    @Test
    @WithTrustJwt("user.json")
    void basicAuthenticationGetsAStepUpChallenge() throws Exception {
        mockMvc.perform(get("/sensitive").with(testSecurityContext()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("acr_values=\"strong\"")));
    }

    @Test
    @WithTrustJwt("user-strong.json")
    void strongAuthenticationIsGranted() throws Exception {
        // Same user, token carrying acr=strong (as issued after a step-up).
        mockMvc.perform(get("/sensitive").with(testSecurityContext())).andExpect(status().isOk());
    }

    @Test
    @WithTrustJwt("manager.json")
    void writeIsDecidedWithTheDocumentTenantAsContext() throws Exception {
        decisions.permit("Document", "write", context -> "tenant-a".equals(context.get("tenant")));

        mockMvc.perform(put("/documents/1").with(testSecurityContext())).andExpect(status().isOk());
        assertThat(decisions.lastRequest()).map(AuthorizationRequest::context)
                .hasValueSatisfying(context -> assertThat(context).containsEntry("tenant", "tenant-a"));

        mockMvc.perform(put("/documents/2").with(testSecurityContext())).andExpect(status().isForbidden());
        assertThat(decisions.lastRequest()).map(AuthorizationRequest::context)
                .hasValueSatisfying(context -> assertThat(context).containsEntry("tenant", "tenant-b"));
    }

    @Test
    @WithTrustJwt("user.json")
    void readIsDeniedWhenTheDecisionPointSaysSo() throws Exception {
        mockMvc.perform(get("/documents/1").with(testSecurityContext())).andExpect(status().isForbidden());

        decisions.permit("Document", "read");
        mockMvc.perform(get("/documents/1").with(testSecurityContext())).andExpect(status().isOk());
    }
}
