package com.bakery.api.modules.production.repositories;

import com.bakery.api.modules.production.entities.ProductionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductionPlanRepository extends JpaRepository<ProductionPlan, UUID> {

    Optional<ProductionPlan> findByPlanDate(LocalDate planDate);

    List<ProductionPlan> findAllByStatusOrderByPlanDateDesc(String status);

    @Query("""
        SELECT p FROM ProductionPlan p
        LEFT JOIN FETCH p.lines l
        LEFT JOIN FETCH l.product
        WHERE p.id = :id
        """)
    Optional<ProductionPlan> findByIdWithLines(@Param("id") UUID id);
}
