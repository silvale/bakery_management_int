package com.bakery.common.repository;

import com.bakery.common.entity.InventoryTransactionLine;
import com.bakery.common.entity.enums.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryTransactionLineRepository extends JpaRepository<InventoryTransactionLine, UUID> {

    List<InventoryTransactionLine> findByTransactionId(UUID transactionId);

    /**
     * Lấy tất cả dòng đã gắn vào lô inventory cụ thể — audit trail FEFO deduction.
     */
    List<InventoryTransactionLine> findByLotId(UUID lotId);

    /**
     * Tổng qty đã xuất cho 1 item từ 1 lô — tính remaining capacity.
     */
    @Query("""
        SELECT COALESCE(SUM(tl.qtyApproved), 0)
        FROM InventoryTransactionLine tl
        JOIN tl.transaction t
        WHERE tl.lot.id    = :lotId
          AND t.status     = 'ACTIVE'
        """)
    java.math.BigDecimal sumApprovedQtyFromLot(@Param("lotId") UUID lotId);

    /**
     * Tìm dòng theo item + transaction — dùng khi clone phiếu.
     */
    @Query("""
        SELECT tl FROM InventoryTransactionLine tl
        WHERE tl.transaction.id = :transactionId
          AND tl.itemId         = :itemId
          AND tl.itemType       = :itemType
        """)
    List<InventoryTransactionLine> findByTransactionAndItem(
        @Param("transactionId") UUID transactionId,
        @Param("itemId")        UUID itemId,
        @Param("itemType")      ItemType itemType
    );
}
