package com.bakery.api.framework.services;

import com.bakery.api.framework.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import com.bakery.api.modules.inventory.repositories.GoodsTransferRepository;
import com.bakery.api.modules.inventory.repositories.InventoryAdjustmentRepository;
import com.bakery.api.modules.inventory.repositories.InventoryWriteOffRepository;
import com.bakery.api.modules.inventory.repositories.StockTransferRepository;
import com.bakery.api.modules.partner.repositories.SupplierReturnRepository;
import com.bakery.api.modules.production.repositories.ProductionRequestRepository;
import com.bakery.api.modules.sales.repositories.CustomerOrderRepository;

@Service
@RequiredArgsConstructor
public class CodeSequenceService {

    private final GoodsTransferRepository goodsTransferRepository;
    private final ProductionRequestRepository productionRequestRepository;
    private final StockTransferRepository stockTransferRepository;
    private final InventoryWriteOffRepository inventoryWriteOffRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final SupplierReturnRepository supplierReturnRepository;
    private final CustomerOrderRepository customerOrderRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public String nextGoodsTransferCode(LocalDate date) {
        // GoodsTransferRepository uses countByTransferDate
        long count = goodsTransferRepository.countByTransferDate(date);
        String prefix = "TRF-" + date.format(DATE_FMT);
        return prefix + "-" + String.format("%03d", count + 1);
    }

    public String nextProductionRequestCode(LocalDate date) {
        // ProductionRequestRepository uses countByCreatedAtBetween
        OffsetDateTime start = date.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        OffsetDateTime end = date.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        long count = productionRequestRepository.countByCreatedAtBetween(start, end);
        String prefix = "PRQ-" + date.format(DATE_FMT);
        return prefix + "-" + String.format("%03d", count + 1);
    }

    public String nextStockTransferCode(LocalDate date) {
        // StockTransferRepository uses countByTransferDate
        long count = stockTransferRepository.countByTransferDate(date);
        String prefix = "STF-" + date.format(DATE_FMT);
        return prefix + "-" + String.format("%03d", count + 1);
    }

    public String nextWriteOffCode(LocalDate date) {
        String prefix = "WOF-" + date.format(DATE_FMT);
        long count = inventoryWriteOffRepository.countByCodeStartingWith(prefix);
        return prefix + "-" + String.format("%03d", count + 1);
    }

    public String nextAdjustmentCode(LocalDate date) {
        String prefix = "ADJ-" + date.format(DATE_FMT);
        long count = inventoryAdjustmentRepository.countByCodeStartingWith(prefix);
        return prefix + "-" + String.format("%03d", count + 1);
    }

    public String nextSupplierReturnCode(LocalDate date) {
        String prefix = "SRT-" + date.format(DATE_FMT);
        long count = supplierReturnRepository.countByCodeStartingWith(prefix);
        return prefix + "-" + String.format("%03d", count + 1);
    }

    public String nextCustomerOrderCode(LocalDate date) {
        // CustomerOrderRepository uses countByCreatedAtBetween
        OffsetDateTime start = date.atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        OffsetDateTime end = date.plusDays(1).atStartOfDay().atOffset(java.time.ZoneOffset.UTC);
        long count = customerOrderRepository.countByCreatedAtBetween(start, end);
        String prefix = "ORD-" + date.format(DATE_FMT);
        return prefix + "-" + String.format("%03d", count + 1);
    }
}
