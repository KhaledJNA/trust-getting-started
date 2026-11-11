package com.trust.sample.bff.session;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public probe the page calls on load: is there a session, and for whom.
 * Never redirects to login (a fetch could not follow it to Keycloak).
 */
@RestController
class SessionController {

    @GetMapping("/api/session")
    Map<String, Object> session(Authentication authentication) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (!(authentication instanceof OAuth2AuthenticationToken token)
                || !(token.getPrincipal() instanceof OidcUser user)) {
            body.put("authenticated", false);
            body.put("loginUrl", "/oauth2/authorization/trust-sample");
            return body;
        }
        body.put("authenticated", true);
        body.put("user", user.getPreferredUsername());
        body.put("acr", user.getClaimAsString("acr"));
        body.put("authorities", token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).filter(a -> a.startsWith("ROLE_")).sorted().toList());
        return body;
    }
}
