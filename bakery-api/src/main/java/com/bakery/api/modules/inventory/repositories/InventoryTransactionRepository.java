package com.bakery.api.modules.inventory.repositories;

import com.bakery.api.modules.inventory.entities.InventoryTransaction;
import com.bakery.api.framework.enums.TransactionStatus;
import com.bakery.api.framework.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {

    Optional<InventoryTransaction> findByCode(String code);

    boolean existsByCode(String code);

    // -------------------------------------------------------
    // Tab-based list queries (ACTIVE / PENDING / REJECTED)
    // -------------------------------------------------------

    /**
     * List phiếu theo type + status — dùng cho 3 tab UI.
     * TRANSFER PENDING: hiện ở cả KHO_TONG (from) và KHO_BEP/SHOP (to).
     */
    /** List + pageable — dùng cho TransactionCommandService.listByStatus() */
    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.transactionType = :type
          AND t.status          = :status
        """)
    Page<InventoryTransaction> findByTypeAndStatus(
        @Param("type")    TransactionType type,
        @Param("status")  TransactionStatus status,
        Pageable pageable
    );

    /** List không page — dùng cho internal queries */
    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.transactionType = :type
          AND t.status          = :status
        ORDER BY t.transactionDate DESC, t.createdAt DESC
        """)
    List<InventoryTransaction> findByTypeAndStatusList(
        @Param("type")   TransactionType type,
        @Param("status") TransactionStatus status
    );

    /**
     * List TRANSFER cho 1 chi nhánh cụ thể (from hoặc to) — phân quyền branch.
     */
    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.transactionType = 'TRANSFER'
          AND t.status          = :status
          AND (t.fromBranch.id = :branchId OR t.toBranch.id = :branchId)
        ORDER BY t.transactionDate DESC, t.createdAt DESC
        """)
    List<InventoryTransaction> findTransfersByBranchAndStatus(
        @Param("branchId") UUID branchId,
        @Param("status")   TransactionStatus status
    );

    /**
     * List IMPORT cho 1 chi nhánh (to_branch_id).
     */
    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.transactionType  = 'IMPORT'
          AND t.status           = :status
          AND t.toBranch.id      = :branchId
        ORDER BY t.transactionDate DESC, t.createdAt DESC
        """)
    List<InventoryTransaction> findImportsByBranchAndStatus(
        @Param("branchId") UUID branchId,
        @Param("status")   TransactionStatus status
    );

    /**
     * Reconciliation: lấy delivery từ BEP trong ngày (TRANSFER ACTIVE → SHOP).
     */
    @Query("""
        SELECT tl.itemId, SUM(tl.qtyApproved)
        FROM InventoryTransaction t
        JOIN t.lines tl
        WHERE t.transactionType  = 'TRANSFER'
          AND t.status           = 'ACTIVE'
          AND t.toBranch.id      = :shopBranchId
          AND t.transactionDate  = :date
        GROUP BY tl.itemId
        """)
    List<Object[]> sumDeliveredToShopByDate(
        @Param("shopBranchId") UUID shopBranchId,
        @Param("date")         LocalDate date
    );

    /**
     * List ADJUSTMENT cho 1 chi nhánh (to_branch_id = kho bị điều chỉnh).
     */
    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.transactionType = 'ADJUSTMENT'
          AND t.status          = :status
          AND t.toBranch.id     = :branchId
        ORDER BY t.transactionDate DESC, t.createdAt DESC
        """)
    List<InventoryTransaction> findAdjustmentsByBranchAndStatus(
        @Param("branchId") UUID branchId,
        @Param("status")   TransactionStatus status
    );

    /**
     * List IMPORT + ADJUSTMENT cho 1 chi nhánh theo nhiều status — dùng cho tab filter.
     */
    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.transactionType = :type
          AND t.status          IN :statuses
          AND t.toBranch.id     = :branchId
        ORDER BY t.transactionDate DESC, t.createdAt DESC
        """)
    List<InventoryTransaction> findByTypeAndStatusInAndToBranch(
        @Param("type")      TransactionType type,
        @Param("statuses")  List<TransactionStatus> statuses,
        @Param("branchId")  UUID branchId
    );

    /**
     * Tìm theo ngày transaction (dùng cho batch reconcile).
     */
    List<InventoryTransaction> findByTransactionDate(LocalDate transactionDate);
}
