package com.bakery.api.service;

import com.bakery.common.entity.*;
import com.bakery.common.entity.enums.BranchType;
import com.bakery.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final StockTransferRepository stockTransferRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final ActivityLogRepository activityLogRepository;
    private final CodeSequenceService codeSequenceService;

    @Transactional
    public Map<String, Object> createTransfer(UUID productId, BigDecimal qtySent, String unit,
                                               LocalDate transferDate, String note, String createdBy) {
        Branch fromBranch = branchRepository.findByBranchType(BranchType.KHO_BEP)
            .orElseThrow(() -> new IllegalStateException("KHO_BEP branch not found"));
        Branch toBranch = branchRepository.findByBranchType(BranchType.SHOP)
            .orElseThrow(() -> new IllegalStateException("SHOP branch not found"));
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        LocalDate date = transferDate != null ? transferDate : LocalDate.now();

        StockTransfer transfer = StockTransfer.builder()
            .fromBranch(fromBranch)
            .toBranch(toBranch)
            .product(product)
            .transferDate(date)
            .qtySent(qtySent)
            .unit(unit != null ? unit : "PCS")
            .status("PENDING")
            .build();

        stockTransferRepository.save(transfer);
        log.info("Created StockTransfer {} -> {} product={} qty={}",
            fromBranch.getName(), toBranch.getName(), product.getCode(), qtySent);

        return toDetailMap(transfer);
    }

    @Transactional
    public Map<String, Object> confirmTransfer(UUID id, BigDecimal qtyReceived, String confirmedBy) {
        StockTransfer transfer = stockTransferRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("StockTransfer not found: " + id));
        if (!"PENDING".equals(transfer.getStatus())) {
            throw new IllegalStateException("Transfer is not PENDING, current status: " + transfer.getStatus());
        }
        transfer.setStatus("CONFIRMED");
        transfer.setQtyReceived(qtyReceived);
        transfer.setConfirmedBy(confirmedBy);
        transfer.setConfirmedAt(OffsetDateTime.now());
        stockTransferRepository.save(transfer);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(confirmedBy)
            .action("CONFIRM_STOCK_TRANSFER")
            .entityType("StockTransfer")
            .entityId(transfer.getId())
            .entityCode(transfer.getProduct().getCode() + "/" + transfer.getTransferDate())
            .oldStatus("PENDING")
            .newStatus("CONFIRMED")
            .createdAt(OffsetDateTime.now())
            .build());

        return toDetailMap(transfer);
    }

    @Transactional
    public Map<String, Object> rejectTransfer(UUID id, String rejectedBy, String reason) {
        StockTransfer transfer = stockTransferRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("StockTransfer not found: " + id));
        if (!"PENDING".equals(transfer.getStatus())) {
            throw new IllegalStateException("Transfer is not PENDING, current status: " + transfer.getStatus());
        }
        transfer.setStatus("REJECTED");
        transfer.setConfirmedBy(rejectedBy);
        transfer.setConfirmedAt(OffsetDateTime.now());
        transfer.setRejectionReason(reason);
        stockTransferRepository.save(transfer);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(rejectedBy)
            .action("REJECT_STOCK_TRANSFER")
            .entityType("StockTransfer")
            .entityId(transfer.getId())
            .entityCode(transfer.getProduct().getCode() + "/" + transfer.getTransferDate())
            .oldStatus("PENDING")
            .newStatus("REJECTED")
            .note(reason)
            .createdAt(OffsetDateTime.now())
            .build());

        return toDetailMap(transfer);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTransfers(LocalDate date, String status) {
        List<StockTransfer> transfers;

        if (date != null && status != null) {
            transfers = stockTransferRepository.findAllByTransferDateOrderByCreatedAtDesc(date).stream()
                .filter(t -> status.equals(t.getStatus()))
                .collect(Collectors.toList());
        } else if (date != null) {
            transfers = stockTransferRepository.findAllByTransferDateOrderByCreatedAtDesc(date);
        } else if (status != null) {
            transfers = stockTransferRepository.findAllByStatusOrderByTransferDateDesc(status);
        } else {
            transfers = stockTransferRepository.findAll().stream()
                .sorted(Comparator.comparing(StockTransfer::getTransferDate).reversed())
                .collect(Collectors.toList());
        }

        return transfers.stream().map(this::toMap).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTransfer(UUID id) {
        StockTransfer transfer = stockTransferRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("StockTransfer not found: " + id));
        return toDetailMap(transfer);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Map<String, Object> toMap(StockTransfer t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("productId", t.getProduct().getId());
        m.put("productCode", t.getProduct().getCode());
        m.put("productName", t.getProduct().getName());
        m.put("fromBranch", t.getFromBranch().getName());
        m.put("toBranch", t.getToBranch().getName());
        m.put("transferDate", t.getTransferDate().toString());
        m.put("qtySent", t.getQtySent());
        m.put("qtyReceived", t.getQtyReceived());
        m.put("qtyDiscrepancy", t.getQtyDiscrepancy());
        m.put("unit", t.getUnit());
        m.put("status", t.getStatus());
        m.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> toDetailMap(StockTransfer t) {
        Map<String, Object> m = toMap(t);
        m.put("confirmedBy", t.getConfirmedBy());
        m.put("confirmedAt", t.getConfirmedAt() != null ? t.getConfirmedAt().toString() : null);
        m.put("rejectionReason", t.getRejectionReason());
        return m;
    }
}
