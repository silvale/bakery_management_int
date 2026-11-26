package com.bakery.api.production.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.master.service.WarehouseRoles;
import com.bakery.api.production.dto.ProductionPoolDto.MaterialCost;
import com.bakery.api.production.dto.ProductionPoolDto.MaterialRow;
import com.bakery.api.production.dto.ProductionPoolDto.Materials;
import com.bakery.api.production.entity.ProductionRequest;
import com.bakery.api.production.repository.ProductionRequestRepository;
import com.bakery.framework.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 24/§6, §8: NL của một phiếu SX (cần / đang giữ / đã dùng) và chi phí NL. Thay cho danh sách lấy NL của 20. */
@Service
@RequiredArgsConstructor
public class ProductionMaterialView {

    private final ProductionRequestRepository requestRepository;
    private final ProductionNeeds needs;
    private final ItemLookupRepository itemRepository;
    private final WarehouseRoles roles;
    private final EntityManager em;

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Materials materials(UUID prId) {
        ProductionRequest pr = requestRepository.findById(prId).orElseThrow(() -> new ResourceNotFoundException("ProductionRequest", prId));
        Warehouse wip = roles.workInProgress();
        Map<UUID, BigDecimal> needed = new LinkedHashMap<>();
        Map<UUID, Item> items = new LinkedHashMap<>();
        needs.forRequest(pr, false).forEach((id, n) -> { needed.put(id, n.qty()); items.put(id, n.item()); });

        Map<UUID, BigDecimal> held = new LinkedHashMap<>();
        for (Object[] r : (List<Object[]>) em.createNativeQuery(
                "SELECT item_id, SUM(qty_remaining) FROM stock_lot WHERE warehouse_id = :w AND reserved_for_request_id = :p AND qty_remaining > 0 GROUP BY item_id")
                .setParameter("w", wip.getId()).setParameter("p", prId).getResultList()) {
            held.put((UUID) r[0], (BigDecimal) r[1]);
        }
        Map<UUID, BigDecimal> consumed = new LinkedHashMap<>();
        for (Object[] r : (List<Object[]>) em.createNativeQuery(
                "SELECT l.item_id, SUM(m.qty) FROM stock_movement m JOIN stock_lot l ON l.id = m.lot_id "
                + "WHERE m.ref_id = :p AND m.ref_type = 'PRODUCTION_REQUEST' AND m.movement_type = 'OUT' AND l.warehouse_id = :w GROUP BY l.item_id")
                .setParameter("p", prId).setParameter("w", wip.getId()).getResultList()) {
            consumed.put((UUID) r[0], (BigDecimal) r[1]);
        }
        java.util.Set<UUID> ids = new java.util.LinkedHashSet<>(needed.keySet());
        ids.addAll(held.keySet());
        ids.addAll(consumed.keySet());
        List<MaterialRow> rows = new java.util.ArrayList<>();
        for (UUID id : ids) {
            Item it = items.get(id);
            if (it == null) it = itemRepository.findById(id).orElse(null);
            if (it == null) continue;
            rows.add(new MaterialRow(id, it.getName(), it.getUnit(), needed.getOrDefault(id, BigDecimal.ZERO),
                    held.getOrDefault(id, BigDecimal.ZERO), consumed.getOrDefault(id, BigDecimal.ZERO)));
        }
        return new Materials(rows);
    }

    /** Đã dùng = Σ OUT từ kho bảng tạm gắn phiếu × giá lô; hư hỏng = Σ amount của WRITE_OFF gắn phiếu. */
    @Transactional(readOnly = true)
    public MaterialCost cost(UUID prId) {
        Warehouse wip = roles.workInProgress();
        BigDecimal used = scalar("SELECT COALESCE(SUM(m.qty * l.unit_cost), 0) FROM stock_movement m JOIN stock_lot l ON l.id = m.lot_id "
                + "WHERE m.ref_id = :p AND m.ref_type = 'PRODUCTION_REQUEST' AND m.movement_type = 'OUT' AND l.warehouse_id = :w", prId, wip.getId());
        BigDecimal damaged = scalar("SELECT COALESCE(SUM(rl.amount), 0) FROM inventory_request_line rl JOIN inventory_request r ON r.id = rl.inventory_request_id "
                + "WHERE r.production_request_id = :p AND r.request_type = 'WRITE_OFF' AND :w IS NOT NULL", prId, wip.getId());
        used = used.setScale(2, RoundingMode.HALF_UP);
        damaged = damaged.setScale(2, RoundingMode.HALF_UP);
        return new MaterialCost(used, damaged, used.add(damaged));
    }

    private BigDecimal scalar(String sql, UUID p, UUID w) {
        Object v = em.createNativeQuery(sql).setParameter("p", p).setParameter("w", w).getSingleResult();
        return v == null ? BigDecimal.ZERO : new BigDecimal(v.toString());
    }
}
