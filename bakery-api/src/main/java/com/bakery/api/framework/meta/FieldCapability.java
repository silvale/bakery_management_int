package com.bakery.api.framework.meta;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đặt trên field của Response DTO để mô tả khả năng filter/sort/search.
 *
 * EntityMetaService đọc annotation này qua reflection → build EntityMeta.
 * AdminBaseResource expose kết quả qua GET /meta.
 * FE dùng GET /meta để render filter panel, sort options, search hints.
 *
 * Ví dụ:
 * <pre>
 * // Tìm kiếm text theo code + name
 * {@literal @}FieldCapability(searchable = true, filterable = true, dataType = DataType.STRING,
 *              operators = {FilterOperator.EQ, FilterOperator.ILIKE})
 * private String code;
 *
 * // Filter dropdown cho enum
 * {@literal @}FieldCapability(filterable = true, dataType = DataType.ENUM)
 * private BranchType branchType;
 *
 * // Sort theo giá
 * {@literal @}FieldCapability(sortable = true, dataType = DataType.NUMBER)
 * private BigDecimal price;
 *
 * // Filter theo ngày
 * {@literal @}FieldCapability(filterable = true, sortable = true, dataType = DataType.DATE,
 *              operators = {FilterOperator.GTE, FilterOperator.LTE, FilterOperator.BETWEEN})
 * private LocalDate effectiveDate;
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldCapability {

    /**
     * Tên field trên entity (dùng trong Specification/Predicate).
     * Mặc định = tên field của Response DTO.
     */
    String entityField() default "";

    /**
     * Kiểu dữ liệu. Mặc định AUTO → tự suy ra từ Java type.
     */
    DataType dataType() default DataType.AUTO;

    /**
     * Field này có thể dùng để filter không?
     * Nếu true → FE hiển thị filter input cho field này.
     */
    boolean filterable() default false;

    /**
     * Operators được phép cho field này.
     * Nếu để trống → dùng operators mặc định theo dataType.
     */
    FilterOperator[] operators() default {};

    /**
     * Field này có thể dùng cho full-text search không?
     * Nếu true → field được include khi gọi ?search=xxx
     */
    boolean searchable() default false;

    /**
     * Field này có thể sort không?
     * Nếu true → FE hiển thị sort icon.
     */
    boolean sortable() default false;

    /**
     * Label hiển thị cho FE (tuỳ chọn).
     * Mặc định để trống → FE tự derive từ tên field.
     */
    String label() default "";

    /**
     * Thứ tự hiển thị trong listing table / filter panel.
     */
    int order() default 0;

    /**
     * Các giá trị enum (chỉ dùng khi dataType = ENUM).
     * Nếu để trống → EntityMetaService tự extract từ Java enum class.
     */
    String[] enumValues() default {};

    /**
     * Tên entity reference (chỉ dùng khi dataType = REFERENCE).
     * VD: "Branch", "Ingredient" — FE dùng để gọi lookup API.
     */
    String referenceEntity() default "";
}
