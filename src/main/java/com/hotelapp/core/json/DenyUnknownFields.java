package com.hotelapp.core.json;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a request DTO as serde {@code deny_unknown_fields}: JSON bodies that
 * carry fields outside the declared record components are rejected instead of
 * silently ignored. The serde default (tolerate unknowns) applies to every
 * unmarked DTO — which matters because Jackson 3 defaults
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} to off.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DenyUnknownFields {
}
