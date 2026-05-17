package com.bakery.common.repository;

import com.bakery.common.entity.ProductionLot;
import com.bakery.common.entity.enums.LotCostStatus;
import com.bakery.common.entity.enums.LotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductionLotRepository extends JpaRepository<ProductionLot, UUID> {

    Optional<ProductionLot> findByLotNumber(String lotNumber);

    List<ProductionLot> findAllByProductIdAndProductionDate(UUID productId, LocalDate date);

    /** Lô sắp hết hạn trong N ngày — dùng cho danh sách cần hủy */
    @Query("""
        SELECT pl FROM ProductionLot pl
        JOIN FETCH pl.product
        WHERE pl.branch.id    = :branchId
          AND pl.status       = 'ACTIVE'
          AND pl.expiryDate  <= :warningDate
          AND pl.qtyRemaining > 0
        ORDER BY pl.expiryDate ASC, pl.product.code ASC
        """)
    List<ProductionLot> findExpiringLots(
        @Param("branchId")    UUID branchId,
        @Param("warningDate") LocalDate warningDate
    );

    /** Lô có cost PENDING — cần backdate */
    @Query("""
        SELECT pl FROM ProductionLot pl
        JOIN FETCH pl.product
        WHERE pl.costStatus = 'PENDING'
        ORDER BY pl.productionDate ASC
        """)
    List<ProductionLot> findPendingCostLots();

    /** Đếm sequence trong ngày để sinh lot_number */
    @Query("""
        SELECT COUNT(pl) FROM ProductionLot pl
        WHERE pl.product.id    = :productId
          AND pl.productionDate = :date
        """)
    long countByProductAndDate(
        @Param("productId") UUID productId,
        @Param("date")      LocalDate date
    );

    /** Lô Lan con của 1 khổ lớn */
    List<ProductionLot> findAllByParentLotId(UUID parentLotId);

    /** FIFO Cancel: lô còn hàng, sắp hết hạn trước */
    @Query("""
        SELECT pl FROM ProductionLot pl
        WHERE pl.product.id = :productId
          AND pl.branch.id  = :branchId
          AND pl.status     = 'ACTIVE'
          AND pl.qtyRemaining > 0
        ORDER BY pl.expiryDate ASC, pl.productionDate ASC
        """)
    List<ProductionLot> findActiveLotsByProductOrderByExpiry(
        @Param("productId") UUID productId,
        @Param("branchId")  UUID branchId
    );

        /** Cập nhật cost sau backdate */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ProductionLot pl
        SET pl.costPerUnit = :costPerUnit,
            pl.costStatus  = 'CONFIRMED'
        WHERE pl.id = :lotId
        """)
    int updateCost(@Param("lotId") UUID lotId, @Param("costPerUnit") java.math.BigDecimal costPerUnit);
}
