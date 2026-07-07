package com.bakery.api.framework.meta;

/**
 * Các toán tử filter được hỗ trợ.
 * FE dùng operator này khi gọi GET /active?... để lọc dữ liệu.
 *
 * Ví dụ:
 *   code.eq=ING001
 *   name.like=bột
 *   price.between=10000,50000
 *   type.in=PHOI,NHAN
 */
public enum FilterOperator {

    /** = (exact match) */
    EQ,

    /** != (not equal) */
    NE,

    /** LIKE %value% (case-sensitive) */
    LIKE,

    /** ILIKE %value% (case-insensitive) — dùng cho text search */
    ILIKE,

    /** Starts with value */
    STARTS_WITH,

    /** IN (value1, value2, ...) */
    IN,

    /** NOT IN */
    NOT_IN,

    /** > */
    GT,

    /** >= */
    GTE,

    /** < */
    LT,

    /** <= */
    LTE,

    /** BETWEEN value1 AND value2 */
    BETWEEN,

    /** IS NULL */
    IS_NULL,

    /** IS NOT NULL */
    IS_NOT_NULL
}
