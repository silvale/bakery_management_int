package com.bakery.framework.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.MultiValueMap;

import jakarta.persistence.criteria.Predicate;

/**
 * Builds JPA Specification from HTTP query params.
 *
 * Supported patterns:
 *   field=value          → exact match
 *   field.like=value     → ILIKE %value%
 *   field.in=a,b,c       → IN (a, b, c)
 *   status=ACTIVE        → exact match on status
 *
 * Reserved params (ignored): page, size, sort
 */
public class SpecificationBuilder {

    private static final Set<String> RESERVED = Set.of("page", "size", "sort");

    /** Hỗ trợ dot-notation: "warehouse.code" → root.join("warehouse").get("code") */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <E> jakarta.persistence.criteria.Path pathGet(
            jakarta.persistence.criteria.Root<E> root, String field) {
        if (field.contains(".")) {
            String[] parts = field.split("\\.", 2);
            return root.join(parts[0], jakarta.persistence.criteria.JoinType.LEFT).get(parts[1]);
        }
        return root.get(field);
    }

    /** Chuyển String value sang đúng kiểu Java của path (để Hibernate 6 không reject type mismatch) */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object coerce(jakarta.persistence.criteria.Path path, String value) {
        Class<?> javaType = path.getJavaType();
        if (javaType == LocalDate.class)      return LocalDate.parse(value);
        if (javaType == LocalDateTime.class)  return LocalDateTime.parse(value);
        if (javaType == UUID.class)           return UUID.fromString(value);
        if (javaType == Integer.class || javaType == int.class) return Integer.parseInt(value);
        if (javaType == Long.class || javaType == long.class)   return Long.parseLong(value);
        if (javaType == Boolean.class || javaType == boolean.class) return Boolean.parseBoolean(value);
        return value; // String or Enum — Hibernate xử lý Enum từ String tự động
    }

    public static <E> Specification<E> from(MultiValueMap<String, String> params) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            params.forEach((key, values) -> {
                if (values == null || values.isEmpty()) return;
                String value = values.getFirst();
                if (value == null || value.isBlank()) return;

                if (RESERVED.contains(key)) return;

                if (key.endsWith(".like")) {
                    String field = key.substring(0, key.length() - 5);
                    predicates.add(cb.like(cb.lower(pathGet(root, field)), "%" + value.toLowerCase() + "%"));
                } else if (key.endsWith(".in")) {
                    String field = key.substring(0, key.length() - 3);
                    List<String> items = List.of(value.split(","));
                    predicates.add(pathGet(root, field).in(items));
                } else {
                    try {
                        var path = pathGet(root, key);
                        predicates.add(cb.equal(path, coerce(path, value)));
                    } catch (Exception ignored) {
                        // skip unknown fields or parse errors
                    }
                }
            });

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
