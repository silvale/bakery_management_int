package com.bakery.api.inventory.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.bakery.api.auth.service.AccessScope;
import com.bakery.framework.entity.EntityStatus;
import com.bakery.framework.util.SpecificationBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.MultiValueMap;
import com.bakery.api.inventory.dto.StockLotResponse;
import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.framework.metadata.ReferenceValue;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Read-only service cho StockLot.
 * Lots được tạo tự động bởi InventoryRequestService.afterApprove() — không tạo/sửa/xóa qua API.
 */
@Service
@RequiredArgsConstructor
public class StockLotService extends AbstractBakeryAdminService<StockLot, Void, StockLotResponse> {

    private final StockLotRepository repository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;

    @Override protected BaseRepository<StockLot> getRepository() { return repository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "StockLot"; }
    @Override protected boolean isAutoApprove() { return true; }

    @Override
    protected StockLot toEntity(Void request) {
        throw new UnsupportedOperationException("Lô hàng chỉ được tạo khi duyệt phiếu nhập");
    }

    @Override
    protected void applyUpdate(StockLot entity, Void request) {
        throw new UnsupportedOperationException("Không sửa lô hàng trực tiếp được");
    }

    private final AccessScope scope;

    /** 11/D2: chỉ lô thuộc kho được gán (Admin: tất cả). */
    private Specification<StockLot> inScope() {
        if (scope.isAdmin()) return (r, q, cb) -> cb.conjunction();
        Set<UUID> ids = scope.warehouseIds();
        return (r, q, cb) -> ids.isEmpty() ? cb.disjunction() : r.get("warehouse").get("id").in(ids);
    }

    @Override
    public Page<StockLotResponse> findAll(MultiValueMap<String, String> params, Pageable pageable) {
        // 11/BE-8: warehouseCode (như /summary) → kho phải thuộc phạm vi (403) và lọc theo kho đó
        MultiValueMap<String, String> rest = new org.springframework.util.LinkedMultiValueMap<>(params);
        String code = rest.getFirst("warehouseCode");
        rest.remove("warehouseCode");
        Specification<StockLot> spec = SpecificationBuilder.from(rest);
        if (code != null && !code.isBlank()) {
            scope.assertWarehouseCode(code);
            spec = spec.and((r, q, cb) -> cb.equal(r.get("warehouse").get("code"), code));
        }
        return repository.findAll(spec.and(inScope()), pageable).map(this::toResponse);
    }

    @Override
    public List<StockLotResponse> findAll() {
        return repository.findAll(inScope().and((r, q, cb) -> cb.equal(r.get("status"), EntityStatus.ACTIVE)))
                .stream().map(this::toResponse).toList();
    }

    @Override
    public Optional<StockLotResponse> findById(UUID id) {
        return repository.findById(id).map(e -> {
            scope.assertWarehouse(e.getWarehouse());
            return toResponse(e);
        });
    }

    @Override
    protected StockLotResponse toResponse(StockLot e) {
        StockLotResponse r = new StockLotResponse();
        r.applyFrom(e);
        if (e.getItem() != null) {
            r.setItem(new ReferenceValue(e.getItem().getCode(), e.getItem().getName()));
        }
        if (e.getWarehouse() != null) {
            r.setWarehouse(new ReferenceValue(e.getWarehouse().getCode(), e.getWarehouse().getName()));
        }
        if (e.getSupplier() != null) {
            r.setSupplier(new ReferenceValue(e.getSupplier().getCode(), e.getSupplier().getName()));
        }
        r.setQtyInitial(e.getQtyInitial());
        r.setQtyRemaining(e.getQtyRemaining());
        // 11/D3, D5: giá lô theo NCC của lô; lô không NCC (sản xuất, điều chỉnh) chỉ Admin
        r.setUnitCost(scope.canSeePurchasePrice(e.getSupplier() != null ? e.getSupplier().getId() : null) ? e.getUnitCost() : null);
        r.setPriceHidden(r.getUnitCost() == null && e.getUnitCost() != null);
        r.setReceivedDate(e.getReceivedDate());
        r.setExpiryDate(e.getExpiryDate());
        return r;
    }
}
