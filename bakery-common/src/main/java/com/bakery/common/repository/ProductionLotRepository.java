package com.bakery.common.repository;

import com.bakery.common.entity.ProductionLot;
import com.bakery.common.entity.enums.LotCostStatus;
import com.bakery.common.entity.enums.LotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductionLotRepository extends JpaRepository<ProductionLot, UUID> {

    Optional<ProductionLot> findByLotNumber(String lotNumber);

    List<ProductionLot> findAllByProductIdAndProductionDate(UUID productId, LocalDate date);

    /**
     * Lô hết hạn HÔM NAY hoặc NGÀY MAI — dùng cho danh sách cần hủy (HuyBanh).
     *
     * Điều kiện:
     *   - expiryDate >= processDate  : chưa hết hạn trước hôm nay (loại bỏ lô cũ chưa đánh EXPIRED)
     *   - expiryDate <= warningDate  : hết hạn hôm nay hoặc ngày mai (warningDate = processDate + 1)
     *   - status = ACTIVE            : chưa bị hủy / hết hàng
     *   - qty_remaining > 0          : còn hàng cần hủy
     *
     * Nếu thiếu cận dưới (expiryDate >= processDate), query sẽ kéo vào tất cả lô cũ
     * từ trước đó vẫn còn ACTIVE (chưa được đánh EXPIRED) → file HuyBanh sai.
     */
    @Query("""
        SELECT pl FROM ProductionLot pl
        JOIN FETCH pl.product
        WHERE pl.branch.id    = :branchId
          AND pl.status       = 'ACTIVE'
          AND pl.expiryDate  >= :processDate
          AND pl.expiryDate  <= :warningDate
          AND pl.qtyRemaining > 0
        ORDER BY pl.expiryDate ASC, pl.product.code ASC
        """)
    List<ProductionLot> findExpiringLots(
        @Param("branchId")    UUID branchId,
        @Param("processDate") LocalDate processDate,
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

        /**
     * Tìm lô theo IN_CODE + ngày sản xuất + chi nhánh.
     * Dùng khi cập nhật qtySold từ file POS.
     */
    @Query("""
        SELECT pl FROM ProductionLot pl
        JOIN FETCH pl.product p
        WHERE p.code           = :productCode
          AND pl.productionDate = :productionDate
          AND pl.branch.id      = :branchId
          AND pl.status        != 'CANCELLED'
        ORDER BY pl.productionDate ASC
        """)
    List<ProductionLot> findByProductCodeAndProductionDate(
        @Param("productCode")    String productCode,
        @Param("productionDate") LocalDate productionDate,
        @Param("branchId")       UUID branchId
    );

    /**
     * Tổng SL sản xuất theo sản phẩm + ngày + chi nhánh.
     * Dùng để đối chiếu với "Bánh sáng" trong BaoCaoNgay (CK2).
     */
    @Query("""
        SELECT COALESCE(SUM(pl.qtyProduced), 0) FROM ProductionLot pl
        WHERE pl.product.id     = :productId
          AND pl.branch.id      = :branchId
          AND pl.productionDate = :date
          AND pl.status        != 'CANCELLED'
        """)
    BigDecimal sumQtyProducedByProductAndDate(
        @Param("productId") UUID productId,
        @Param("branchId")  UUID branchId,
        @Param("date")      LocalDate date
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
