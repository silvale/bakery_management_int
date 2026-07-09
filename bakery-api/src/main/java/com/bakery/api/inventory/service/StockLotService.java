package com.bakery.api.inventory.service;

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
        throw new UnsupportedOperationException("StockLot is created via purchase approval only");
    }

    @Override
    protected void applyUpdate(StockLot entity, Void request) {
        throw new UnsupportedOperationException("StockLot cannot be updated via API");
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
        r.setUnitCost(e.getUnitCost());
        r.setReceivedDate(e.getReceivedDate());
        r.setExpiryDate(e.getExpiryDate());
        return r;
    }
}
