package com.bakery.framework.metadata;

public enum ReferenceType {
    /** Tham chiếu đến một entity khác (corporation, supplier, warehouse...) */
    ENTITY,

    /** Tham chiếu đến code value table (unit, ingredient type...) */
    CODE_VALUE
}
