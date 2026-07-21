package com.bakery.api.production.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.production.entity.ProductionPlanGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductionPlanGroupRepository extends JpaRepository<ProductionPlanGroup, UUID> {

    List<ProductionPlanGroup> findByPlanId(UUID planId);

    Optional<ProductionPlanGroup> findByPlanIdAndGroupId(UUID planId, UUID groupId);

    @Query("SELECT ppg FROM ProductionPlanGroup ppg JOIN FETCH ppg.group g LEFT JOIN FETCH g.baseRecipe WHERE ppg.plan.id = :planId")
    List<ProductionPlanGroup> findByPlanIdWithGroup(@Param("planId") UUID planId);
}
