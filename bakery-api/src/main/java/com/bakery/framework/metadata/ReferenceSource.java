package com.bakery.framework.metadata;

/**
 * Constants for {@link Reference#source()}.
 * Declare new keys here as modules are added.
 */
public final class ReferenceSource {

    private ReferenceSource() {}

    // ── Entities ─────────────────────────────────────────────
    public static final String SUPPLIER  = "SUPPLIER";
    public static final String WAREHOUSE = "WAREHOUSE";
    public static final String INGREDIENT = "INGREDIENT";
    public static final String PRODUCT   = "PRODUCT";

    // ── Code values — master ──────────────────────────────────
    public static final String UNIT              = "UNIT";
    public static final String INGREDIENT_TYPE   = "INGREDIENT_TYPE";
    public static final String PRODUCT_TYPE      = "PRODUCT_TYPE";
    public static final String PRODUCT_CATEGORY  = "PRODUCT_CATEGORY";
    public static final String WAREHOUSE_TYPE    = "WAREHOUSE_TYPE";
    public static final String TRANSACTION_TYPE  = "TRANSACTION_TYPE";

    // ── Code values — reference type (grouped by transaction) ─
    public static final String REF_TYPE_IMPORT = "REF_TYPE_IMPORT";
    public static final String REF_TYPE_EXPORT = "REF_TYPE_EXPORT";
    public static final String REF_TYPE_ADJ    = "REF_TYPE_ADJ";
    public static final String REF_TYPE_CANCEL = "REF_TYPE_CANCEL";
    public static final String REF_TYPE_RETURN = "REF_TYPE_RETURN";
}
