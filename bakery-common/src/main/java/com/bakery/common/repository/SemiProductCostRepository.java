package com.bakery.common.repository;

import com.bakery.common.entity.SemiProductCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SemiProductCostRepository extends JpaRepository<SemiProductCost, UUID> {

    /**
     * Lookup cost/kg đúng tại ngày X.
     * Tương tự ingredient_price: lấy version có effective_date <= targetDate.
     */
    @Query("""
        SELECT sc FROM SemiProductCost sc
        WHERE sc.semiProduct.id = :semiProductId
          AND sc.effectiveDate <= :targetDate
        ORDER BY sc.effectiveDate DESC
        LIMIT 1
        """)
    Optional<SemiProductCost> findActiveCost(
        @Param("semiProductId") UUID semiProductId,
        @Param("targetDate")    LocalDate targetDate
    );

    @Query("""
        SELECT sc FROM SemiProductCost sc
        WHERE sc.semiProduct.id = :semiProductId
        ORDER BY sc.effectiveDate DESC
        LIMIT 1
        """)
    Optional<SemiProductCost> findLatestCost(@Param("semiProductId") UUID semiProductId);

    @Query("SELECT COALESCE(MAX(sc.version), 0) FROM SemiProductCost sc WHERE sc.semiProduct.id = :semiProductId")
    int findMaxVersion(@Param("semiProductId") UUID semiProductId);
}
