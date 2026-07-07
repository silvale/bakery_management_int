package com.bakery.api.modules.inventory.repositories;

import com.bakery.api.modules.inventory.entities.GoodsTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoodsTransferRepository extends JpaRepository<GoodsTransfer, UUID> {

    Optional<GoodsTransfer> findByCode(String code);

    long countByTransferDate(LocalDate date);

    // ── KHO_TONG screen ───────────────────────────────────────
    List<GoodsTransfer> findAllByFromBranchIdAndStatusOrderByTransferDateDesc(
        UUID fromBranchId, String status);

    // ── KHO_BEP screen ────────────────────────────────────────
    List<GoodsTransfer> findAllByToBranchIdAndStatusOrderByTransferDateDesc(
        UUID toBranchId, String status);

    // ── Dashboard Chính: pending adjustments ──────────────────
    List<GoodsTransfer> findAllByTransferReasonAndStatusOrderByTransferDateDesc(
        String transferReason, String status);

    // ── General ───────────────────────────────────────────────
    List<GoodsTransfer> findAllByStatusOrderByTransferDateDesc(String status);

    List<GoodsTransfer> findAllByTransferDateBetweenOrderByTransferDateDesc(
        LocalDate from, LocalDate to);

    @Query("""
        SELECT t FROM GoodsTransfer t
        LEFT JOIN FETCH t.lines l
        LEFT JOIN FETCH l.ingredient
        LEFT JOIN FETCH l.product
        WHERE t.id = :id
        """)
    Optional<GoodsTransfer> findByIdWithLines(@Param("id") UUID id);
}
