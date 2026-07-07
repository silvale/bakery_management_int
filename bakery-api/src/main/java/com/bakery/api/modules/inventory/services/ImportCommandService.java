package com.bakery.api.modules.inventory.services;

import com.bakery.api.framework.exceptions.AdminValidationException;
import com.bakery.api.framework.services.TransactionCommandService;
import com.bakery.api.modules.inventory.dtos.ImportRequest;
import com.bakery.api.modules.inventory.dtos.ImportResponse;
import com.bakery.api.modules.inventory.dtos.TransactionLineResponse;
import com.bakery.api.modules.inventory.entities.Inventory;
import com.bakery.api.modules.inventory.entities.InventoryTransaction;
import com.bakery.api.modules.inventory.entities.InventoryTransactionLine;

import com.bakery.api.framework.enums.ItemType;
import com.bakery.api.framework.enums.TransactionReason;
import com.bakery.api.framework.enums.TransactionType;
import com.bakery.api.modules.masterdata.repositories.BranchRepository;
import com.bakery.api.modules.inventory.repositories.InventoryRepository;
import com.bakery.api.modules.inventory.repositories.InventoryTransactionRepository;
import com.bakery.api.modules.partner.repositories.SupplierRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Concrete service cho phiếu NHẬP HÀNG (transaction_type = IMPORT).
 *
 * afterApprove():
 *   - Tạo Inventory lot mới cho mỗi line (FEFO lot với expiry_date nếu có)
 *   - Cập nhật qty_available theo qty_approved (hoặc qty_requested nếu null)
 */
@Slf4j
@Service
public class ImportCommandService extends TransactionCommandService<ImportRequest, ImportResponse> {

    private final BranchRepository branchRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryRepository inventoryRepository;

    public ImportCommandService(
            InventoryTransactionRepository txRepository,
            BranchRepository branchRepository,
            SupplierRepository supplierRepository,
            InventoryRepository inventoryRepository) {
        super(txRepository);
        this.branchRepository   = branchRepository;
        this.supplierRepository = supplierRepository;
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public TransactionType transactionType() {
        return TransactionType.IMPORT;
    }

    // ── Build entity từ request ───────────────────────────────────────────────

    @Override
    protected InventoryTransaction buildTransaction(ImportRequest req, String actor) {
        var tx = InventoryTransaction.builder()
                .code(generateCode())
                .transactionType(TransactionType.IMPORT)
                .transactionReason(req.transactionReason())
                .transactionDate(req.transactionDate() != null ? req.transactionDate() : LocalDate.now())
                .totalAmount(req.totalAmount() != null ? req.totalAmount() : BigDecimal.ZERO)
                .paymentStatus(req.paymentStatus())
                .note(req.note())
                .build();

        // Kho nhận
        tx.setToBranch(branchRepository.findById(req.toBranchId())
                .orElseThrow(() -> new AdminValidationException("Branch không tồn tại: " + req.toBranchId())));

        // Nhà cung cấp (optional cho PRODUCTION/RESTOCK)
        if (req.supplierId() != null) {
            tx.setSupplier(supplierRepository.findById(req.supplierId())
                    .orElseThrow(() -> new AdminValidationException("Supplier không tồn tại: " + req.supplierId())));
        }

        // Lines
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
    protected void applyUpdate(InventoryTransaction tx, ImportRequest req) {
        tx.setTransactionDate(req.transactionDate() != null ? req.transactionDate() : tx.getTransactionDate());
        tx.setTransactionReason(req.transactionReason());
        tx.setNote(req.note());
        tx.setPaymentStatus(req.paymentStatus());
        if (req.totalAmount() != null) tx.setTotalAmount(req.totalAmount());

        if (req.supplierId() != null) {
            tx.setSupplier(supplierRepository.findById(req.supplierId())
                    .orElseThrow(() -> new AdminValidationException("Supplier không tồn tại: " + req.supplierId())));
        }

        // Replace lines
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

    // ── Validate ─────────────────────────────────────────────────────────────

    @Override
    protected void validateCreate(ImportRequest req) {
        if (req.transactionReason() == TransactionReason.PURCHASE && req.supplierId() == null) {
            throw new AdminValidationException("PURCHASE cần có supplier_id.");
        }
        if (req.lines() == null || req.lines().isEmpty()) {
            throw new AdminValidationException("Phiếu nhập cần ít nhất 1 dòng hàng.");
        }
    }

    // ── afterApprove: tạo Inventory lots ─────────────────────────────────────

    @Override
    protected void afterApprove(InventoryTransaction tx) {
        // Force-fetch toBranch within current session to avoid lazy-proxy "no session" error.
        // tx.getToBranch() is a Hibernate proxy (LAZY) — re-fetch by ID guarantees initialization.
        var branch = branchRepository.findById(tx.getToBranch().getId())
                .orElseThrow(() -> new AdminValidationException("Branch không tồn tại"));

        // Re-fetch lines via repository to ensure they are loaded in current session
        for (InventoryTransactionLine line : tx.getLines()) {
            BigDecimal qty = line.getQtyApproved() != null ? line.getQtyApproved() : line.getQtyRequested();
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            Inventory lot = Inventory.builder()
                    .branch(branch)
                    .itemId(line.getItemId())
                    .itemType(line.getItemType())
                    .qtyAvailable(qty)
                    .costPerUnit(line.getUnitPrice())
                    .sourceTxId(tx.getId())
                    .build();

            Inventory savedLot = inventoryRepository.save(lot);
            // Gắn lot_id vào line để audit trail
            line.setLot(savedLot);
        }
        txRepository.save(tx);
        log.info("[IMPORT] afterApprove: tạo {} inventory lots for tx={}", tx.getLines().size(), tx.getId());
    }

    // ── toResponse ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ImportResponse toResponse(InventoryTransaction tx) {
        ImportResponse res = new ImportResponse();
        res.setId(tx.getId());
        res.setCode(tx.getCode());
        res.setStatus(tx.getStatus());
        res.setTransactionDate(tx.getTransactionDate());
        res.setTransactionReason(tx.getTransactionReason());
        res.setTotalAmount(tx.getTotalAmount());
        res.setPaymentStatus(tx.getPaymentStatus());
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
            res.setToBranchId(tx.getToBranch().getId());
            res.setToBranchName(tx.getToBranch().getName());
        }
        if (tx.getSupplier() != null) {
            res.setSupplierId(tx.getSupplier().getId());
            res.setSupplierName(tx.getSupplier().getName());
        }

        List<TransactionLineResponse> lines = tx.getLines().stream()
                .map(this::toLineResponse)
                .toList();
        res.setLines(lines);

        return res;
    }

    private TransactionLineResponse toLineResponse(InventoryTransactionLine line) {
        BigDecimal qty = line.getQtyApproved() != null ? line.getQtyApproved() : line.getQtyRequested();
        BigDecimal lineTotal = qty.multiply(line.getUnitPrice());
        return new TransactionLineResponse(
                line.getId(),
                line.getItemId(),
                line.getItemType(),
                null,   // itemName — resolve nếu cần (join ingredient/product)
                line.getQtyRequested(),
                line.getQtyApproved(),
                line.getUnit(),
                line.getUnitPrice(),
                lineTotal,
                line.getLot() != null ? line.getLot().getId() : null,
                line.getNote()
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String generateCode() {
        // Format: IMP-YYYYMMDD-XXXX
        String date = LocalDate.now().toString().replace("-", "");
        String suffix = String.format("%04d", (int) (Math.random() * 9999) + 1);
        return "IMP-" + date + "-" + suffix;
    }
}
