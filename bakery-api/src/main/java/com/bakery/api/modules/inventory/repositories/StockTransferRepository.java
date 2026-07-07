package com.bakery.api.modules.inventory.repositories;

import com.bakery.api.modules.inventory.entities.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    Optional<StockTransfer> findByFromBranchIdAndToBranchIdAndProductIdAndTransferDate(
        UUID fromBranchId, UUID toBranchId, UUID productId, LocalDate transferDate
    );

    List<StockTransfer> findAllByFromBranchIdAndTransferDate(UUID fromBranchId, LocalDate transferDate);

    List<StockTransfer> findAllByToBranchIdAndTransferDate(UUID toBranchId, LocalDate transferDate);

    /** Lấy danh sách theo status (dùng String thay ReconcileStatus) */
    List<StockTransfer> findAllByStatusNot(String status);

    List<StockTransfer> findAllByStatusOrderByTransferDateDesc(String status);

    List<StockTransfer> findAllByTransferDateOrderByCreatedAtDesc(LocalDate transferDate);

    long countByTransferDate(LocalDate transferDate);

    List<StockTransfer> findAllByTransferDateBetweenOrderByTransferDateDesc(LocalDate from, LocalDate to);

    @Query("""
        SELECT st FROM StockTransfer st
        WHERE st.fromBranch.id = :fromBranchId
          AND st.toBranch.id = :toBranchId
          AND st.transferDate = :transferDate
        ORDER BY st.product.code
        """)
    List<StockTransfer> findAllByBranchesAndDate(
        @Param("fromBranchId") UUID fromBranchId,
        @Param("toBranchId")   UUID toBranchId,
        @Param("transferDate") LocalDate transferDate
    );
}
