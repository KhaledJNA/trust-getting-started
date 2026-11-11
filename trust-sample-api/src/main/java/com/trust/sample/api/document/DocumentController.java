package com.trust.sample.api.document;

import com.trust.security.core.authorization.AuthorizationResourceId;
import com.trust.security.core.authorization.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Fine-grained authorization decided by Keycloak Authorization Services
 * (UMA), not by roles checked in the code:
 * <ul>
 *   <li>read: permission "Document read" → any {@code SAMPLE_USER};</li>
 *   <li>write: permission "Document write" → {@code SAMPLE_MANAGER}
 *   <em>and</em> the document's tenant is {@code tenant-a} (odd ids): the
 *   same manager is refused on an even id.</li>
 * </ul>
 */
@RestController
class DocumentController {

    @GetMapping("/documents/{id}")
    @RequiresPermission(resource = "Document", scope = "read")
    Map<String, Object> read(@PathVariable long id) {
        return Map.of("id", id, "tenant", DocumentTenantContextProvider.tenantOf(id), "action", "read");
    }

    @PutMapping("/documents/{id}")
    @CanWriteDocument
    Map<String, Object> write(@AuthorizationResourceId(DocumentTenantContextProvider.class) @PathVariable long id) {
        return Map.of("id", id, "tenant", DocumentTenantContextProvider.tenantOf(id), "action", "write");
    }
}
