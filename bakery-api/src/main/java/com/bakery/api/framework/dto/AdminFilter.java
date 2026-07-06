package com.bakery.api.framework.dto;

import com.bakery.common.entity.enums.EntityStatus;
import lombok.Getter;
import lombok.Setter;

/**
 * Base filter cho list/search trong admin UI.
 * Các entity-specific filter extends class này.
 */
@Getter
@Setter
public class AdminFilter {

    /** Tìm kiếm theo text (code, name, ...) */
    private String search;

    /** Lọc theo entity_status — null = tất cả */
    private EntityStatus entityStatus;

    private int page = 0;
    private int size = 20;
}
