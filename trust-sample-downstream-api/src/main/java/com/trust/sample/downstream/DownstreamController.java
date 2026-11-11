package com.trust.sample.downstream;

import com.trust.security.core.user.AuthenticatedUser;
import com.trust.security.core.user.CurrentUserProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What each endpoint shows:
 * <ul>
 *   <li>{@code /public/ping}: a public path ({@code trust.security.resource-server.public-paths});</li>
 *   <li>{@code /data}: a user call. {@code issuedTo} is the client the token
 *   was issued to - {@code trust-sample-bff} when the BFF relays the
 *   user's own token, {@code trust-sample-api} when that API exchanged it
 *   for a token of its own (RFC 8693) - the user stays the same;</li>
 *   <li>{@code /system/status}: an application call, allowed only with the
 *   {@code sample.system} scope, which only {@code trust-sample-api} can
 *   obtain for itself ({@code client_credentials}).</li>
 * </ul>
 */
@RestController
class DownstreamController {

    private final CurrentUserProvider currentUserProvider;

    DownstreamController(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/public/ping")
    Map<String, String> ping() {
        return Map.of("service", "trust-sample-downstream-api", "status", "UP");
    }

    @GetMapping("/data")
    @PreAuthorize("hasRole('SAMPLE_USER')")
    Map<String, Object> data(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "trust-sample-downstream-api");
        body.put("user", currentUserProvider.getCurrentUser().map(AuthenticatedUser::username).orElse(null));
        body.put("issuedTo", principal.getAttribute("client_id"));
        return body;
    }

    @GetMapping("/system/status")
    @PreAuthorize("hasAuthority('SCOPE_sample.system')")
    Map<String, Object> systemStatus(@AuthenticationPrincipal OAuth2AuthenticatedPrincipal principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "trust-sample-downstream-api");
        body.put("status", "UP");
        body.put("calledBy", principal.getAttribute("client_id"));
        return body;
    }
}
