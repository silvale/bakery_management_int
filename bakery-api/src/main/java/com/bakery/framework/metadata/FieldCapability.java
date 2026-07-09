package com.bakery.framework.metadata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares UI and query capabilities for a DTO/entity field.
 *
 * <p>Usage examples:
 * <pre>
 * // Entity reference — links to another entity via code
 * {@code @FieldCapability(
 *     reference   = @Reference(type = ReferenceType.ENTITY, source = "CORPORATION", keyType = KeyType.CODE),
 *     entityField = "corporationCode",
 *     searchable  = true,
 *     label       = "Tập đoàn")}
 * private ReferenceValue corporation;
 *
 * // Code-value dropdown
 * {@code @FieldCapability(
 *     reference   = @Reference(type = ReferenceType.CODE_VALUE, source = "UNIT"),
 *     entityField = "unitCode",
 *     filterable  = true,
 *     label       = "Đơn vị")}
 * private ReferenceValue unit;
 *
 * // Plain searchable field
 * {@code @FieldCapability(searchable = true, sortable = true, label = "Tên")}
 * private String name;
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FieldCapability {

    /**
     * Reference config — entity or code-value lookup.
     * Leave default (empty source) for non-reference fields.
     */
    Reference reference() default @Reference(type = ReferenceType.CODE_VALUE, source = "");

    /**
     * The actual field on the entity that stores the persisted key.
     * e.g., "corporationCode", "unitCode"
     */
    String entityField() default "";

    // ── Query capabilities ────────────────────────────────────

    /** Field can be used in ?field.like=... search */
    boolean searchable() default false;

    /** Field appears in filter panel on UI */
    boolean filterable() default false;

    /** Field can be used for sorting */
    boolean sortable() default false;

    // ── UI ───────────────────────────────────────────────────

    /** Hide field on UI */
    boolean hidden() default false;

    /** Display label on UI */
    String label() default "";

    // ── Module linking ────────────────────────────────────────

    /** Module this field belongs to, for cross-module linking */
    String module() default "";

    /** Sub-module within the module */
    String subModule() default "";
}
