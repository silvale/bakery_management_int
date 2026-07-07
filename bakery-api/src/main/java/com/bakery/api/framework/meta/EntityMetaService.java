package com.bakery.api.framework.meta;

import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Đọc @FieldCapability annotations từ Response DTO class → build EntityMeta.
 *
 * Cách dùng trong SupportService:
 * <pre>
 * public EntityMeta getMeta() {
 *     return entityMetaService.buildMeta("Ingredient", IngredientResponse.class);
 * }
 * </pre>
 */
@Service
public class EntityMetaService {

    /**
     * Scan tất cả fields của responseClass có @FieldCapability → build EntityMeta.
     *
     * @param entityType   Tên entity (VD: "Ingredient", "Branch")
     * @param responseClass Class của Response DTO
     */
    public EntityMeta buildMeta(String entityType, Class<?> responseClass) {
        List<FieldMeta> fields = new ArrayList<>();
        scanFields(responseClass, fields);
        fields.sort(Comparator.comparingInt(FieldMeta::order));
        return new EntityMeta(entityType, fields);
    }

    // ── Private helpers ───────────────────────────────────────

    /** Scan class hierarchy (bao gồm superclass như BakeryBaseResponse). */
    private void scanFields(Class<?> clazz, List<FieldMeta> result) {
        if (clazz == null || clazz == Object.class) return;

        // Scan superclass trước (BakeryBaseResponse fields)
        scanFields(clazz.getSuperclass(), result);

        for (Field field : clazz.getDeclaredFields()) {
            FieldCapability cap = field.getAnnotation(FieldCapability.class);
            if (cap == null) continue;

            String fieldName   = field.getName();
            DataType dataType  = resolveDataType(cap.dataType(), field.getType());
            String entityField = cap.entityField().isBlank() ? fieldName : cap.entityField();
            String label       = cap.label().isBlank() ? toLabel(fieldName) : cap.label();

            List<FilterOperator> operators = resolveOperators(cap.operators(), dataType);
            List<String> enumValues = resolveEnumValues(cap.enumValues(), field.getType(), dataType);

            result.add(new FieldMeta(
                    fieldName,
                    entityField,
                    label,
                    dataType,
                    cap.filterable(),
                    operators,
                    cap.searchable(),
                    cap.sortable(),
                    cap.order(),
                    enumValues,
                    cap.referenceEntity()
            ));
        }
    }

    /**
     * Suy ra DataType từ Java field type nếu annotation dùng AUTO.
     */
    private DataType resolveDataType(DataType declared, Class<?> javaType) {
        if (declared != DataType.AUTO) return declared;

        if (javaType == String.class)                           return DataType.STRING;
        if (javaType == Boolean.class || javaType == boolean.class) return DataType.BOOLEAN;
        if (javaType == UUID.class)                             return DataType.UUID;
        if (javaType == LocalDate.class)                        return DataType.DATE;
        if (javaType == LocalDateTime.class || javaType == OffsetDateTime.class) return DataType.DATETIME;
        if (Number.class.isAssignableFrom(javaType)
                || javaType == int.class || javaType == long.class
                || javaType == double.class || javaType == float.class) return DataType.NUMBER;
        if (BigDecimal.class.isAssignableFrom(javaType))       return DataType.NUMBER;
        if (javaType.isEnum())                                  return DataType.ENUM;

        return DataType.STRING; // fallback
    }

    /**
     * Lấy operators từ annotation.
     * Nếu annotation không khai báo → dùng default theo dataType.
     */
    private List<FilterOperator> resolveOperators(FilterOperator[] declared, DataType dataType) {
        if (declared.length > 0) return Arrays.asList(declared);

        return switch (dataType) {
            case STRING   -> List.of(FilterOperator.EQ, FilterOperator.NE, FilterOperator.ILIKE);
            case NUMBER   -> List.of(FilterOperator.EQ, FilterOperator.NE, FilterOperator.GTE, FilterOperator.LTE, FilterOperator.BETWEEN);
            case BOOLEAN  -> List.of(FilterOperator.EQ);
            case DATE, DATETIME -> List.of(FilterOperator.GTE, FilterOperator.LTE, FilterOperator.BETWEEN);
            case ENUM     -> List.of(FilterOperator.EQ, FilterOperator.NE, FilterOperator.IN);
            case UUID     -> List.of(FilterOperator.EQ, FilterOperator.IN);
            case REFERENCE -> List.of(FilterOperator.EQ, FilterOperator.IN);
            default        -> List.of(FilterOperator.EQ);
        };
    }

    /**
     * Lấy enum values.
     * Ưu tiên annotation khai báo; nếu không → tự extract từ Java enum class.
     */
    private List<String> resolveEnumValues(String[] declared, Class<?> javaType, DataType dataType) {
        if (declared.length > 0) return Arrays.asList(declared);
        if (dataType == DataType.ENUM && javaType.isEnum()) {
            return Arrays.stream(javaType.getEnumConstants())
                    .map(Object::toString)
                    .toList();
        }
        return List.of();
    }

    /** Chuyển camelCase → "Camel Case" để dùng làm label mặc định. */
    private String toLabel(String fieldName) {
        String spaced = fieldName.replaceAll("([A-Z])", " $1").trim();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
