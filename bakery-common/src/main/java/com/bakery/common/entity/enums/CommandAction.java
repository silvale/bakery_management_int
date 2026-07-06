package com.bakery.common.entity.enums;

/**
 * Hành động trong command_request.
 * CREATE → tạo mới entity
 * UPDATE → cập nhật entity
 * DELETE → vô hiệu hóa entity (soft delete)
 */
public enum CommandAction {
    CREATE,
    UPDATE,
    DELETE
}
