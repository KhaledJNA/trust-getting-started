package com.trust.sample.api.document;

import com.trust.security.core.authorization.PermissionContextProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Tells Keycloak which tenant a document belongs to: Trust sends this map
 * as the UMA request's {@code claim_token}, and the realm's "Tenant A
 * context policy" (a built-in Regex policy on context attributes) grants
 * {@code write} only for {@code tenant-a}.
 * <p>
 * A real application would look the tenant up in its database; this sample
 * uses a fixed rule: even ids belong to {@code tenant-b}, odd ids to
 * {@code tenant-a}.
 */
@Component
public class DocumentTenantContextProvider implements PermissionContextProvider {

    public static String tenantOf(long documentId) {
        return documentId % 2 == 0 ? "tenant-b" : "tenant-a";
    }

    @Override
    public Map<String, String> resolve(Object resourceId) {
        return Map.of("tenant", tenantOf(((Number) resourceId).longValue()));
    }
}
