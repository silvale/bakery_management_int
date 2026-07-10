package com.bakery.api.inventory.repository;

import java.time.LocalDate;
import java.util.List;
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
}
