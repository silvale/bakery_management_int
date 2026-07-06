package com.bakery.common.entity.enums;

/**
 * Loại hàng hóa — dùng trong inventory.item_type và inventory_transaction_line.item_type (VARCHAR).
 * Phân biệt item_id tham chiếu tới bảng nào vì không có DB-level FK do polymorphic relationship.
 *
 * INGREDIENT — item_id trỏ tới ingredient.id
 * PRODUCT    — item_id trỏ tới product.id
 */
public enum ItemType {
    INGREDIENT,
    PRODUCT
}
