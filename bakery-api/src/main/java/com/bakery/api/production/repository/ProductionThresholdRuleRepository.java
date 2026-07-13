package com.bakery.api.production.repository;

import java.util.List;
import java.util.UUID;

import com.bakery.api.production.entity.ProductionThresholdRule;
import com.bakery.framework.entity.DayType;
import com.bakery.framework.repository.BaseRepository;

public interface ProductionThresholdRuleRepository extends BaseRepository<ProductionThresholdRule> {

    /** Lấy tất cả rules của 1 item theo dayType, sorted theo sort_order ASC. */
    List<ProductionThresholdRule> findByItemIdAndDayTypeOrderBySortOrderAsc(UUID itemId, DayType dayType);

    /** Xóa toàn bộ rules của 1 item (dùng khi replace rules). */
    void deleteAllByItemId(UUID itemId);
}
