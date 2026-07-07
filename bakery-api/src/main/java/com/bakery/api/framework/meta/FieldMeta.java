package com.bakery.api.framework.meta;

import java.util.List;

/**
 * Metadata của 1 field — trả về trong GET /meta response.
 */
public record FieldMeta(
        /** Tên field trong response DTO */
        String name,

        /** Tên field trên entity (dùng cho Specification) */
        String entityField,

        /** Label hiển thị cho FE */
        String label,

        /** Kiểu dữ liệu */
        DataType dataType,

        /** Có thể filter không */
        boolean filterable,

        /** Operators được phép */
        List<FilterOperator> operators,

        /** Có thể dùng trong ?search=xxx không */
        boolean searchable,

        /** Có thể sort không */
        boolean sortable,

        /** Thứ tự hiển thị */
        int order,

        /** Giá trị enum (nếu dataType = ENUM) */
        List<String> enumValues,

        /** Reference entity (nếu dataType = REFERENCE) */
        String referenceEntity
) {}
