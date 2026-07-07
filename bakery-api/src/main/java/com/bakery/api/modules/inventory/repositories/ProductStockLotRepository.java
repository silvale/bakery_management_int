package com.bakery.api.modules.inventory.repositories;

import com.bakery.api.modules.inventory.entities.ProductStockLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductStockLotRepository extends JpaRepository<ProductStockLot, UUID> {

    List<ProductStockLot> findAllByProductIdAndBranchIdAndIsDepletedFalseOrderByImportDateAsc(
            UUID productId, UUID branchId);

    /** Max Price Engine: giá cao nhất trong các lô còn hàng */
    @Query("""
        SELECT MAX(l.unitPrice)
        FROM ProductStockLot l
        WHERE l.product.id = :productId
          AND l.branch.id  = :branchId
          AND l.isDepleted = FALSE
        """)
    Optional<BigDecimal> findMaxUnitPrice(@Param("productId") UUID productId,
                                          @Param("branchId")  UUID branchId);

    @Query("""
        SELECT SUM(l.qtyRemaining)
        FROM ProductStockLot l
        WHERE l.product.id = :productId
          AND l.branch.id  = :branchId
          AND l.isDepleted = FALSE
        """)
    Optional<BigDecimal> sumRemainingQty(@Param("productId") UUID productId,
                                         @Param("branchId")  UUID branchId);
}
