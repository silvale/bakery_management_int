package com.bakery.api.inventory.service;

import com.bakery.api.framework.exception.AdminValidationException;
import com.bakery.api.framework.service.TransactionCommandService;
import com.bakery.api.inventory.dto.AdjustmentRequest;
import com.bakery.api.inventory.dto.AdjustmentResponse;
import com.bakery.api.inventory.dto.TransactionLineResponse;
import com.bakery.common.entity.Inventory;
import com.bakery.common.entity.InventoryTransaction;
import com.bakery.common.entity.InventoryTransactionLine;

import com.bakery.common.entity.enums.TransactionReason;
import com.bakery.common.entity.enums.TransactionType;
import com.bakery.common.repository.BranchRepository;
import com.bakery.common.repository.InventoryRepository;
import com.bakery.common.repository.InventoryTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Concrete service cho phiếu ĐIỀU CHỈNH (transaction_type = ADJUSTMENT).
 *
 * afterApprove():
 *   qty > 0 → tạo Inventory lot mới (tăng tồn)
 *   qty < 0 → FEFO deduct (giảm tồn)
 *   qty = 0 → bỏ qua
 *
 * Hỗ trợ: LOSS | STOCKTAKE | SUPPLIER_RETURN | WRITE_OFF
 */
@Slf4j
@Service
public class AdjustmentCommandService extends TransactionCommandService<AdjustmentRequest, AdjustmentResponse> {

    private static final Set<TransactionReason> VALID_REASONS = Set.of(
            TransactionReason.LOSS,
            TransactionReason.STOCKTAKE,
            TransactionReason.SUPPLIER_RETURN,
            TransactionReason.WRITE_OFF
    );

    private final BranchRepository branchRepository;
    private final InventoryRepository inventoryRepository;

    public AdjustmentCommandService(
            InventoryTransactionRepository txRepository,
            BranchRepository branchRepository,
            InventoryRepository inventoryRepository) {
        super(txRepository);
        this.branchRepository    = branchRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public TransactionType transactionType() {
        return TransactionType.ADJUSTMENT;
    }

    @Override
    protected InventoryTransaction buildTransaction(AdjustmentRequest req, String actor) {
        var tx = InventoryTransaction.builder()
                .code(generateCode())
                .transactionType(TransactionType.ADJUSTMENT)
                .transactionReason(req.transactionReason())
                .transactionDate(req.transactionDate() != null ? req.transactionDate() : LocalDate.now())
                .note(req.note())
                .build();

        tx.setToBranch(branchRepository.findById(req.branchId())
                .orElseThrow(() -> new AdminValidationException("Branch không tồn tại: " + req.branchId())));

        req.lines().forEach(lineReq -> {
            var line = InventoryTransactionLine.builder()
                    .transaction(tx)
                    .itemId(lineReq.itemId())
                    .itemType(lineReq.itemType())
                    .qtyRequested(lineReq.qtyRequested())
                    .qtyApproved(lineReq.qtyApproved())
                    .unit(lineReq.unit())
                    .unitPrice(lineReq.unitPrice() != null ? lineReq.unitPrice() : BigDecimal.ZERO)
                    .note(lineReq.note())
                    .build();
            tx.getLines().add(line);
        });

        return tx;
    }

    @Override
    protected void applyUpdate(InventoryTransaction tx, AdjustmentRequest req) {
        if (req.transactionDate() != null) tx.setTransactionDate(req.transactionDate());
        tx.setTransactionReason(req.transactionReason());
        tx.setNote(req.note());

        tx.getLines().clear();
        req.lines().forEach(lineReq -> {
            var line = InventoryTransactionLine.builder()
                    .transaction(tx)
                    .itemId(lineReq.itemId())
                    .itemType(lineReq.itemType())
                    .qtyRequested(lineReq.qtyRequested())
                    .qtyApproved(lineReq.qtyApproved())
                    .unit(lineReq.unit())
                    .unitPrice(lineReq.unitPrice() != null ? lineReq.unitPrice() : BigDecimal.ZERO)
                    .note(lineReq.note())
                    .build();
            tx.getLines().add(line);
        });
    }

    @Override
    protected void validateCreate(AdjustmentRequest req) {
        if (!VALID_REASONS.contains(req.transactionReason())) {
            throw new AdminValidationException("Lý do không hợp lệ cho ADJUSTMENT: " + req.transactionReason());
        }
    }

    @Override
    protected void afterApprove(InventoryTransaction tx) {
        for (InventoryTransactionLine line : tx.getLines()) {
            BigDecimal qty = line.getQtyApproved() != null ? line.getQtyApproved() : line.getQtyRequested();
            if (qty.compareTo(BigDecimal.ZERO) == 0) continue;

            if (qty.compareTo(BigDecimal.ZERO) > 0) {
                // Tăng tồn — tạo lot mới
                Inventory lot = Inventory.builder()
                        .branch(tx.getToBranch())
                        .itemId(line.getItemId())
                        .itemType(line.getItemType())
                        .qtyAvailable(qty)
                        .sourceTxId(tx.getId())
                        .build();
                Inventory savedLot = inventoryRepository.save(lot);
                line.setLot(savedLot);
            } else {
                // Giảm tồn — FEFO deduct (qty là số âm, abs để deduct)
                BigDecimal toDeduct = qty.abs();
                deductFefo(tx, line, toDeduct);
            }
        }
        txRepository.save(tx);
        log.info("[ADJUSTMENT] afterApprove: {} lines processed for tx={}", tx.getLines().size(), tx.getId());
    }

    private void deductFefo(InventoryTransaction tx, InventoryTransactionLine line, BigDecimal toDeduct) {
        List<Inventory> lots = inventoryRepository.findAvailableFefo(
                tx.getToBranch().getId(), line.getItemId(), line.getItemType());

        BigDecimal remaining = toDeduct;
        for (Inventory lot : lots) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal deduct = remaining.min(lot.getQtyAvailable());
            lot.setQtyAvailable(lot.getQtyAvailable().subtract(deduct));
            inventoryRepository.save(lot);
            remaining = remaining.subtract(deduct);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("[ADJUSTMENT] Tồn kho không đủ để deduct item={} tại branch={}: thiếu {}",
                    line.getItemId(), tx.getToBranch().getId(), remaining);
        }
    }

    @Override
    public AdjustmentResponse toResponse(InventoryTransaction tx) {
        AdjustmentResponse res = new AdjustmentResponse();
        res.setId(tx.getId());
        res.setCode(tx.getCode());
        res.setStatus(tx.getStatus());
        res.setTransactionDate(tx.getTransactionDate());
        res.setTransactionReason(tx.getTransactionReason());
        res.setNote(tx.getNote());
        res.setRejectionReason(tx.getRejectionReason());
        res.setCreatedBy(tx.getCreatedBy());
        res.setCreatedAt(tx.getCreatedAt());
        res.setUpdatedBy(tx.getUpdatedBy());
        res.setUpdatedAt(tx.getUpdatedAt());
        res.setApprovedBy(tx.getApprovedBy());
        res.setApprovedAt(tx.getApprovedAt());
        res.setEntityStatus(tx.getEntityStatus());

        if (tx.getToBranch() != null) {
            res.setBranchId(tx.getToBranch().getId());
            res.setBranchName(tx.getToBranch().getName());
        }

        res.setLines(tx.getLines().stream().map(line -> {
            BigDecimal qty = line.getQtyApproved() != null ? line.getQtyApproved() : line.getQtyRequested();
            return new TransactionLineResponse(
                    line.getId(), line.getItemId(), line.getItemType(), null,
                    line.getQtyRequested(), line.getQtyApproved(), line.getUnit(),
                    line.getUnitPrice(), qty.abs().multiply(line.getUnitPrice()),
                    line.getLot() != null ? line.getLot().getId() : null, line.getNote());
        }).toList());

        return res;
    }

    private String generateCode() {
        String date = LocalDate.now().toString().replace("-", "");
        String suffix = String.format("%04d", (int) (Math.random() * 9999) + 1);
        return "ADJ-" + date + "-" + suffix;
    }
}
