package com.trust.sample.api.document;

import com.trust.security.core.authorization.RequiresPermission;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An application-level annotation composing {@link RequiresPermission}:
 * the code says what it protects ("write a document"), the resource/scope
 * names Keycloak knows stay in one place.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RequiresPermission(resource = "Document", scope = "write")
public @interface CanWriteDocument {
}
