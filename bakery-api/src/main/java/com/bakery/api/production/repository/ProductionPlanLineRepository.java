package com.bakery.api.production.repository;

import java.util.List;
import java.util.UUID;

import com.bakery.api.production.entity.ProductionPlanLine;
import com.bakery.framework.repository.BaseRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductionPlanLineRepository extends BaseRepository<ProductionPlanLine> {

    List<ProductionPlanLine> findByPlanIdOrderBySortOrderAsc(UUID planId);

    List<ProductionPlanLine> findByPlanIdAndGroupIdOrderBySortOrderAsc(UUID planId, UUID groupId);

    /**
     * Xóa toàn bộ lines của 1 plan — dùng @Modifying + JPQL để flush DELETE ngay xuống DB
     * trước khi insert mới, tránh vi phạm unique constraint (plan_id, item_id).
     */
    @Modifying
    @Query("DELETE FROM ProductionPlanLine l WHERE l.plan.id = :planId")
    void deleteAllByPlanId(@Param("planId") UUID planId);
}
