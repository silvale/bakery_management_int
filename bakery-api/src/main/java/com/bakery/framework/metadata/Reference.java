package com.bakery.framework.metadata;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a reference to an entity or code-value table.
 * Used as a nested element inside {@link FieldCapability}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface Reference {

    /** Type of reference: ENTITY or CODE_VALUE */
    ReferenceType type();

    /**
     * Source key — entity name or code-value key.
     * Declared inline as a String constant when needed.
     * e.g., "CORPORATION", "UNIT", "INGREDIENT_TYPE"
     */
    String source();

    /** Which field on the source to use as the lookup key */
    KeyType keyType() default KeyType.ID;
}
