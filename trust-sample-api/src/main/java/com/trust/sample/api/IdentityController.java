package com.trust.sample.api;

import com.trust.security.core.acr.RequiresAcr;
import com.trust.security.core.user.AuthenticatedUser;
import com.trust.security.core.user.CurrentAuthoritiesProvider;
import com.trust.security.core.user.CurrentUserProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who is calling, and three ways of protecting an endpoint:
 * <ul>
 *   <li>{@code /me}: a realm role ({@code realm_access.roles} → {@code ROLE_SAMPLE_USER});</li>
 *   <li>{@code /reader}: a client role of this API
 *   ({@code resource_access.trust-sample-api.roles} → {@code ROLE_SAMPLE_READER});</li>
 *   <li>{@code /sensitive}: an authentication level ({@code @RequiresAcr("strong")}):
 *   a token without it gets {@code 401} + {@code WWW-Authenticate: Bearer
 *   error="insufficient_user_authentication", acr_values="strong"} (RFC 9470).</li>
 * </ul>
 */
@RestController
class IdentityController {

    private final CurrentUserProvider currentUserProvider;
    private final CurrentAuthoritiesProvider currentAuthoritiesProvider;

    IdentityController(CurrentUserProvider currentUserProvider, CurrentAuthoritiesProvider currentAuthoritiesProvider) {
        this.currentUserProvider = currentUserProvider;
        this.currentAuthoritiesProvider = currentAuthoritiesProvider;
    }

    @GetMapping("/public/ping")
    Map<String, String> ping() {
        return Map.of("service", "trust-sample-api", "status", "UP");
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('SAMPLE_USER')")
    Map<String, Object> me() {
        return identity("trust-sample-api");
    }

    @GetMapping("/reader")
    @PreAuthorize("hasRole('SAMPLE_READER')")
    Map<String, Object> reader() {
        return identity("reader endpoint: client role SAMPLE_READER granted");
    }

    @GetMapping("/sensitive")
    @PreAuthorize("hasRole('SAMPLE_USER')")
    @RequiresAcr("strong")
    Map<String, Object> sensitive() {
        return identity("sensitive endpoint: acr=strong granted");
    }

    private Map<String, Object> identity(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("user", currentUserProvider.getCurrentUser().map(AuthenticatedUser::username).orElse(null));
        body.put("subject", currentUserProvider.getCurrentUser().map(AuthenticatedUser::subject).orElse(null));
        List<String> authorities = currentAuthoritiesProvider.getAuthorities().stream().sorted().toList();
        body.put("authorities", authorities);
        return body;
    }
}
