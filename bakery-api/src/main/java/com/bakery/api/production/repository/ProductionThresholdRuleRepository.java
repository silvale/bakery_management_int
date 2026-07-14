package com.bakery.api.production.repository;

import java.util.List;
import java.util.UUID;

import com.bakery.api.production.entity.ProductionThresholdRule;
import com.bakery.framework.entity.DayType;
import com.bakery.framework.repository.BaseRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductionThresholdRuleRepository extends BaseRepository<ProductionThresholdRule> {

    /** Lấy tất cả rules của 1 item theo dayType, sorted theo sort_order ASC. */
    List<ProductionThresholdRule> findByItemIdAndDayTypeOrderBySortOrderAsc(UUID itemId, DayType dayType);

    /**
     * Xóa toàn bộ rules của 1 item — dùng @Modifying + JPQL để flush ngay xuống DB
     * trước khi insert mới, tránh vi phạm unique constraint (item_id, day_type, sort_order).
     */
    @Modifying
    @Query("DELETE FROM ProductionThresholdRule r WHERE r.item.id = :itemId")
    void deleteAllByItemId(@Param("itemId") UUID itemId);
}
