package com.bakery.api.inventory.service;

import com.bakery.api.framework.exception.AdminValidationException;
import com.bakery.api.framework.service.TransactionCommandService;
import com.bakery.api.inventory.dto.TransferRequest;
import com.bakery.api.inventory.dto.TransferResponse;
import com.bakery.api.inventory.dto.TransactionLineResponse;
import com.bakery.common.entity.Inventory;
import com.bakery.common.entity.InventoryTransaction;
import com.bakery.common.entity.InventoryTransactionLine;

import com.bakery.common.entity.enums.TransactionReason;
import com.bakery.common.entity.enums.TransactionStatus;
import com.bakery.common.entity.enums.TransactionType;
import com.bakery.common.repository.BranchRepository;
import com.bakery.common.repository.InventoryRepository;
import com.bakery.common.repository.InventoryTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Concrete service cho phiếu ĐIỀU CHUYỂN (transaction_type = TRANSFER).
 *
 * 2-step approve:
 *   Bước 1 (Cường): PENDING → READY    — chỉ thay status, chưa động inventory
 *   Bước 2 (Shop/Bếp): READY → ACTIVE — FEFO deduct from_branch, tạo lot ở to_branch
 */
@Slf4j
@Service
public class TransferCommandService extends TransactionCommandService<TransferRequest, TransferResponse> {

    private final BranchRepository branchRepository;
    private final InventoryRepository inventoryRepository;

    public TransferCommandService(
            InventoryTransactionRepository txRepository,
            BranchRepository branchRepository,
            InventoryRepository inventoryRepository) {
        super(txRepository);
        this.branchRepository    = branchRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public TransactionType transactionType() {
        return TransactionType.TRANSFER;
    }

    @Override
    protected InventoryTransaction buildTransaction(TransferRequest req, String actor) {
        var tx = InventoryTransaction.builder()
                .code(generateCode())
                .transactionType(TransactionType.TRANSFER)
                .transactionReason(TransactionReason.RESTOCK)
                .transactionDate(req.transactionDate() != null ? req.transactionDate() : LocalDate.now())
                .note(req.note())
                .build();

        tx.setFromBranch(branchRepository.findById(req.fromBranchId())
                .orElseThrow(() -> new AdminValidationException("fromBranch không tồn tại: " + req.fromBranchId())));
        tx.setToBranch(branchRepository.findById(req.toBranchId())
                .orElseThrow(() -> new AdminValidationException("toBranch không tồn tại: " + req.toBranchId())));

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
    protected void applyUpdate(InventoryTransaction tx, TransferRequest req) {
        if (req.transactionDate() != null) tx.setTransactionDate(req.transactionDate());
        tx.setNote(req.note());

        if (req.fromBranchId() != null) {
            tx.setFromBranch(branchRepository.findById(req.fromBranchId())
                    .orElseThrow(() -> new AdminValidationException("fromBranch không tồn tại: " + req.fromBranchId())));
        }
        if (req.toBranchId() != null) {
            tx.setToBranch(branchRepository.findById(req.toBranchId())
                    .orElseThrow(() -> new AdminValidationException("toBranch không tồn tại: " + req.toBranchId())));
        }

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
    protected void validateCreate(TransferRequest req) {
        if (req.fromBranchId().equals(req.toBranchId())) {
            throw new AdminValidationException("fromBranch và toBranch không được trùng nhau.");
        }
    }

    /**
     * afterApprove chỉ thực thi inventory khi status = ACTIVE (bước 2).
     * Bước 1 (PENDING → READY) không động inventory.
     */
    @Override
    protected void afterApprove(InventoryTransaction tx) {
        if (tx.getStatus() != TransactionStatus.ACTIVE) {
            log.info("[TRANSFER] Bước 1 done: tx={} → READY, chưa deduct inventory", tx.getId());
            return;
        }

        // Bước 2: READY → ACTIVE — FEFO deduct + tạo lot mới ở to_branch
        for (InventoryTransactionLine line : tx.getLines()) {
            BigDecimal qtyToMove = line.getQtyApproved() != null ? line.getQtyApproved() : line.getQtyRequested();
            if (qtyToMove.compareTo(BigDecimal.ZERO) <= 0) continue;

            // FEFO deduct từ from_branch
            deductFefo(tx, line, qtyToMove);

            // Tạo lot mới ở to_branch
            Inventory newLot = Inventory.builder()
                    .branch(tx.getToBranch())
                    .itemId(line.getItemId())
                    .itemType(line.getItemType())
                    .qtyAvailable(qtyToMove)
                    .costPerUnit(line.getUnitPrice().compareTo(BigDecimal.ZERO) > 0
                            ? line.getUnitPrice() : null)
                    .sourceTxId(tx.getId())
                    .build();
            inventoryRepository.save(newLot);
        }
        log.info("[TRANSFER] afterApprove ACTIVE: deducted from_branch={}, added to_branch={} for tx={}",
                tx.getFromBranch().getId(), tx.getToBranch().getId(), tx.getId());
    }

    private void deductFefo(InventoryTransaction tx, InventoryTransactionLine line, BigDecimal qtyToMove) {
        List<Inventory> lots = inventoryRepository.findAvailableFefo(
                tx.getFromBranch().getId(), line.getItemId(), line.getItemType());

        BigDecimal remaining = qtyToMove;
        for (Inventory lot : lots) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal deduct = remaining.min(lot.getQtyAvailable());
            lot.setQtyAvailable(lot.getQtyAvailable().subtract(deduct));
            inventoryRepository.save(lot);
            remaining = remaining.subtract(deduct);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            log.warn("[TRANSFER] Tồn kho không đủ cho item={} tại branch={}: thiếu {}",
                    line.getItemId(), tx.getFromBranch().getId(), remaining);
        }
    }

    @Override
    public TransferResponse toResponse(InventoryTransaction tx) {
        TransferResponse res = new TransferResponse();
        res.setId(tx.getId());
        res.setCode(tx.getCode());
        res.setStatus(tx.getStatus());
        res.setTransactionDate(tx.getTransactionDate());
        res.setNote(tx.getNote());
        res.setRejectionReason(tx.getRejectionReason());
        res.setCreatedBy(tx.getCreatedBy());
        res.setCreatedAt(tx.getCreatedAt());
        res.setUpdatedBy(tx.getUpdatedBy());
        res.setUpdatedAt(tx.getUpdatedAt());
        res.setApprovedBy(tx.getApprovedBy());
        res.setApprovedAt(tx.getApprovedAt());
        res.setEntityStatus(tx.getEntityStatus());

        if (tx.getFromBranch() != null) {
            res.setFromBranchId(tx.getFromBranch().getId());
            res.setFromBranchName(tx.getFromBranch().getName());
        }
        if (tx.getToBranch() != null) {
            res.setToBranchId(tx.getToBranch().getId());
            res.setToBranchName(tx.getToBranch().getName());
        }

        res.setLines(tx.getLines().stream().map(line -> {
            BigDecimal qty = line.getQtyApproved() != null ? line.getQtyApproved() : line.getQtyRequested();
            return new TransactionLineResponse(
                    line.getId(), line.getItemId(), line.getItemType(), null,
                    line.getQtyRequested(), line.getQtyApproved(), line.getUnit(),
                    line.getUnitPrice(), qty.multiply(line.getUnitPrice()),
                    line.getLot() != null ? line.getLot().getId() : null, line.getNote());
        }).toList());

        return res;
    }

    private String generateCode() {
        String date = LocalDate.now().toString().replace("-", "");
        String suffix = String.format("%04d", (int) (Math.random() * 9999) + 1);
        return "TRF-" + date + "-" + suffix;
    }
}
