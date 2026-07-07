package com.bakery.api.framework.meta;

/**
 * Kiểu dữ liệu của field trong Response DTO.
 * FE dùng để render đúng loại input trong filter panel và form.
 *
 * AUTO = tự suy ra từ Java type (String→STRING, BigDecimal→NUMBER, Boolean→BOOLEAN, v.v.)
 */
public enum DataType {

    /** Tự suy ra từ Java field type */
    AUTO,

    /** String — filter: EQ, NE, ILIKE, STARTS_WITH */
    STRING,

    /** Số nguyên hoặc thập phân — filter: EQ, NE, GT, GTE, LT, LTE, BETWEEN */
    NUMBER,

    /** Boolean — filter: EQ */
    BOOLEAN,

    /** LocalDate — filter: EQ, GT, GTE, LT, LTE, BETWEEN */
    DATE,

    /** OffsetDateTime / LocalDateTime — filter: EQ, GTE, LTE, BETWEEN */
    DATETIME,

    /** Enum — filter: EQ, NE, IN, NOT_IN. FE render dropdown với enumValues */
    ENUM,

    /** UUID (id) — filter: EQ, IN */
    UUID,

    /** FK reference tới entity khác — FE render lookup dropdown */
    REFERENCE
}
