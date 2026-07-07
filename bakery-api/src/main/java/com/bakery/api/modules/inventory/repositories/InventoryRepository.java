package com.bakery.api.modules.inventory.repositories;

import com.bakery.api.modules.inventory.entities.Inventory;
import com.bakery.api.framework.enums.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    /**
     * FEFO: lấy các lô còn hàng, sắp xếp theo expiry_date ASC (null cuối cùng).
     * Dùng khi approve phiếu TRANSFER / ADJUSTMENT để deduct tồn kho.
     */
    @Query("""
        SELECT i FROM Inventory i
        WHERE i.branch.id = :branchId
          AND i.itemId    = :itemId
          AND i.itemType  = :itemType
          AND i.qtyAvailable > 0
        ORDER BY i.expiryDate ASC NULLS LAST, i.createdAt ASC
        """)
    List<Inventory> findAvailableFefo(
        @Param("branchId") UUID branchId,
        @Param("itemId")   UUID itemId,
        @Param("itemType") ItemType itemType
    );

    /**
     * Tổng tồn kho của 1 item tại 1 chi nhánh (tất cả lô).
     */
    @Query("""
        SELECT COALESCE(SUM(i.qtyAvailable), 0)
        FROM Inventory i
        WHERE i.branch.id = :branchId
          AND i.itemId    = :itemId
          AND i.itemType  = :itemType
        """)
    java.math.BigDecimal sumQtyAvailable(
        @Param("branchId") UUID branchId,
        @Param("itemId")   UUID itemId,
        @Param("itemType") ItemType itemType
    );

    /**
     * Toàn bộ tồn kho tại 1 chi nhánh — dùng cho màn hình xem kho.
     * Bao gồm cả lô qty = 0 (audit trail), sort theo item_type + expiry_date.
     */
    @Query("""
        SELECT i FROM Inventory i
        WHERE i.branch.id = :branchId
        ORDER BY i.itemType ASC, i.expiryDate ASC NULLS LAST
        """)
    List<Inventory> findAllByBranch(@Param("branchId") UUID branchId);

    /**
     * Chỉ lô còn hàng (qtyAvailable > 0) tại 1 chi nhánh — màn hình tồn kho UI.
     */
    @Query("""
        SELECT i FROM Inventory i
        WHERE i.branch.id    = :branchId
          AND i.qtyAvailable > 0
        ORDER BY i.itemType ASC, i.expiryDate ASC NULLS LAST
        """)
    List<Inventory> findAvailableByBranch(@Param("branchId") UUID branchId);

    /**
     * Lô còn hàng theo itemType (INGREDIENT | PRODUCT) tại 1 chi nhánh.
     */
    @Query("""
        SELECT i FROM Inventory i
        WHERE i.branch.id    = :branchId
          AND i.itemType     = :itemType
          AND i.qtyAvailable > 0
        ORDER BY i.expiryDate ASC NULLS LAST
        """)
    List<Inventory> findAvailableByBranchAndItemType(
        @Param("branchId") UUID branchId,
        @Param("itemType") ItemType itemType
    );

    /**
     * Tất cả lô của 1 item (kể cả đã hết) — audit trail / FEFO detail.
     */
    @Query("""
        SELECT i FROM Inventory i
        WHERE i.branch.id = :branchId
          AND i.itemId    = :itemId
          AND i.itemType  = :itemType
        ORDER BY i.expiryDate ASC NULLS LAST, i.createdAt ASC
        """)
    List<Inventory> findAllLotsByItem(
        @Param("branchId") UUID branchId,
        @Param("itemId")   UUID itemId,
        @Param("itemType") ItemType itemType
    );

    /**
     * Aggregate: tổng tồn theo từng itemId tại 1 chi nhánh — dashboard / summary.
     * Returns Object[]: [itemId (UUID), itemType (String), totalQty (BigDecimal)]
     */
    @Query("""
        SELECT i.itemId, i.itemType, SUM(i.qtyAvailable)
        FROM Inventory i
        WHERE i.branch.id    = :branchId
          AND i.qtyAvailable > 0
        GROUP BY i.itemId, i.itemType
        ORDER BY i.itemType ASC
        """)
    List<Object[]> sumQtyByItemAtBranch(@Param("branchId") UUID branchId);

    /**
     * Các lô sắp hết hạn trong N ngày — dùng cảnh báo (tất cả chi nhánh).
     */
    @Query("""
        SELECT i FROM Inventory i
        WHERE i.qtyAvailable > 0
          AND i.expiryDate IS NOT NULL
          AND i.expiryDate <= :warningDate
        ORDER BY i.expiryDate ASC
        """)
    List<Inventory> findExpiringSoon(@Param("warningDate") LocalDate warningDate);

    /**
     * Sắp hết hạn tại 1 chi nhánh cụ thể.
     */
    @Query("""
        SELECT i FROM Inventory i
        WHERE i.branch.id    = :branchId
          AND i.qtyAvailable > 0
          AND i.expiryDate IS NOT NULL
          AND i.expiryDate <= :warningDate
        ORDER BY i.expiryDate ASC
        """)
    List<Inventory> findExpiringSoonByBranch(
        @Param("branchId")    UUID branchId,
        @Param("warningDate") LocalDate warningDate
    );
}
