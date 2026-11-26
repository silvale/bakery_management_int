package com.bakery.api.inventory.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import com.bakery.api.inventory.entity.InventoryRequest;
import com.bakery.framework.entity.InventoryRequestType;
import com.bakery.framework.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRequestRepository extends BaseRepository<InventoryRequest> {
    Optional<InventoryRequest> findByCode(String code);
    List<InventoryRequest> findByRequestTypeAndRequestDate(InventoryRequestType type, LocalDate date);
    long countByCodeStartingWith(String codePrefix);

    /**
     * Lấy tất cả phiếu liên quan đến 1 kho (source OR target) theo approvalStatus.
     * Dùng cho tab pending của từng kho: MAIN/KITCHEN/SHOP.
     *
     * Native query để đảm bảo LEFT JOIN — JPQL dot-notation dùng INNER JOIN ngầm,
     * làm mất PURCHASE (source_warehouse_id = NULL).
     */
    @Query(value = """
            SELECT r.*
            FROM inventory_request r
            LEFT JOIN warehouse tw ON tw.id = r.target_warehouse_id
            LEFT JOIN warehouse sw ON sw.id = r.source_warehouse_id
            WHERE r.approval_status = :approvalStatus
              AND (tw.code = :warehouseCode OR sw.code = :warehouseCode)
            ORDER BY r.created_at DESC
            """, nativeQuery = true)
    List<InventoryRequest> findByWarehouseAndStatus(
            @Param("warehouseCode") String warehouseCode,
            @Param("approvalStatus") String approvalStatus);

    /**
     * Nạp sẵn lines (+ item, packaging) cho cả trang danh sách bằng 1 query, tránh N+1.
     * Gọi trong cùng session sau findAll(spec, pageable): fetch join khởi tạo collection
     * của các phiếu đã nằm trong persistence context (không cần phân trang trên collection).
     */
    @Query("""
            SELECT DISTINCT r FROM InventoryRequest r
            LEFT JOIN FETCH r.lines l
            LEFT JOIN FETCH l.item
            LEFT JOIN FETCH l.packaging pk
            LEFT JOIN FETCH pk.unit
            WHERE r.id IN :ids
            """)
    List<InventoryRequest> findWithLinesByIdIn(@Param("ids") Collection<UUID> ids);

    /** 24: phiếu bảng tạm của một phiên. */
    List<InventoryRequest> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    /** 24: phiếu lấy tự tạo theo kế hoạch của một ngày (không gắn phiếu SX). */
    List<InventoryRequest> findByRequestTypeAndRequestDateAndPickReasonAndProductionRequestIsNull(
            InventoryRequestType type, LocalDate date, String pickReason);

    /** 24: phiếu bảng tạm gắn một phiếu SX. */
    List<InventoryRequest> findByProductionRequestIdAndRequestType(UUID productionRequestId, InventoryRequestType type);

    /** 24: phiếu PICK / RETURN / WRITE_OFF theo ngày (idx_inv_req_type_date_pick). */
    List<InventoryRequest> findByRequestTypeInAndRequestDateBetweenOrderByRequestDateDescCreatedAtDesc(
            Collection<InventoryRequestType> types, LocalDate from, LocalDate to);

    /** 24: số phiếu bảng tạm theo phiên → [sessionId, count] (idx_inv_req_session). */
    @Query(value = "SELECT session_id, COUNT(*) FROM inventory_request WHERE session_id IN (:ids) GROUP BY session_id", nativeQuery = true)
    List<Object[]> countBySession(@Param("ids") Collection<UUID> ids);
}
