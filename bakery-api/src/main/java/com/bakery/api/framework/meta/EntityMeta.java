package com.bakery.api.framework.meta;

import java.util.List;

/**
 * Metadata của 1 entity — response của GET /meta.
 *
 * FE dùng để:
 *   - Render filter panel (filterable fields + operators)
 *   - Render listing table (sortable columns)
 *   - Build search query (?search=xxx áp dụng vào searchable fields)
 *
 * Ví dụ response:
 * <pre>
 * {
 *   "entityType": "Ingredient",
 *   "fields": [
 *     { "name": "code", "dataType": "STRING", "filterable": true,
 *       "operators": ["EQ","ILIKE"], "searchable": true, "sortable": true },
 *     { "name": "baseUnit", "dataType": "ENUM", "filterable": true,
 *       "operators": ["EQ","IN"], "enumValues": ["KG","GRAM","LITER","PIECE"] }
 *   ]
 * }
 * </pre>
 */
public record EntityMeta(
        String entityType,
        List<FieldMeta> fields
) {}
