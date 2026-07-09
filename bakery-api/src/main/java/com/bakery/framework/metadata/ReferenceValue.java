package com.bakery.framework.metadata;

/**
 * Value object for reference fields.
 * Holds the lookup key and display name — not persisted directly.
 *
 * @param key  the lookup key (code string or UUID string)
 * @param name display name for UI
 */
public record ReferenceValue(String key, String name) {}
