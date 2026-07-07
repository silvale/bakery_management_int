package com.bakery.api.modules.partner.services;

import com.bakery.api.framework.*;
import com.bakery.api.framework.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.bakery.api.framework.services.CodeSequenceService;
import com.bakery.api.modules.inventory.entities.InventoryWriteOff;
import com.bakery.api.modules.inventory.repositories.InventoryWriteOffRepository;
import com.bakery.api.modules.partner.entities.PurchaseOrder;
import com.bakery.api.modules.partner.entities.Supplier;
import com.bakery.api.modules.partner.entities.SupplierReturn;
import com.bakery.api.modules.partner.repositories.PurchaseOrderRepository;
import com.bakery.api.modules.partner.repositories.SupplierRepository;
import com.bakery.api.modules.partner.repositories.SupplierReturnRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierReturnService {

    private final SupplierReturnRepository supplierReturnRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InventoryWriteOffRepository inventoryWriteOffRepository;
    private final ActivityLogRepository activityLogRepository;
    private final CodeSequenceService codeSequenceService;

    @Transactional
    public Map<String, Object> createReturn(UUID supplierId, UUID originalPoId, UUID writeOffId,
                                             LocalDate returnDate, String reason, String createdBy) {
        Supplier supplier = supplierRepository.findById(supplierId)
            .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + supplierId));
        PurchaseOrder originalPo = purchaseOrderRepository.findById(originalPoId)
            .orElseThrow(() -> new IllegalArgumentException("PurchaseOrder not found: " + originalPoId));

        String code = codeSequenceService.nextSupplierReturnCode(LocalDate.now());

        SupplierReturn.SupplierReturnBuilder builder = SupplierReturn.builder()
            .code(code)
            .supplier(supplier)
            .originalPo(originalPo)
            .returnDate(returnDate != null ? returnDate : LocalDate.now())
            .reason(reason)
            .status("PENDING")
            .createdBy(createdBy != null ? createdBy : "system")
            .createdAt(OffsetDateTime.now());

        if (writeOffId != null) {
            InventoryWriteOff writeOff = inventoryWriteOffRepository.findById(writeOffId)
                .orElseThrow(() -> new IllegalArgumentException("InventoryWriteOff not found: " + writeOffId));
            builder.writeOff(writeOff);
        }

        SupplierReturn supplierReturn = builder.build();
        supplierReturnRepository.save(supplierReturn);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(createdBy != null ? createdBy : "system")
            .action("CREATE_SUPPLIER_RETURN")
            .entityType("SupplierReturn")
            .entityId(supplierReturn.getId())
            .entityCode(supplierReturn.getCode())
            .newStatus("PENDING")
            .createdAt(OffsetDateTime.now())
            .build());

        log.info("Created SupplierReturn {} for supplier {}", code, supplier.getName());
        return toDetailMap(supplierReturn);
    }

    @Transactional
    public Map<String, Object> markSentToSupplier(UUID id, String updatedBy) {
        SupplierReturn sr = supplierReturnRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("SupplierReturn not found: " + id));
        String oldStatus = sr.getStatus();
        sr.setStatus("SENT_TO_SUPPLIER");
        supplierReturnRepository.save(sr);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(updatedBy)
            .action("SEND_SUPPLIER_RETURN")
            .entityType("SupplierReturn")
            .entityId(id)
            .entityCode(sr.getCode())
            .oldStatus(oldStatus)
            .newStatus("SENT_TO_SUPPLIER")
            .createdAt(OffsetDateTime.now())
            .build());

        return toMap(sr);
    }

    @Transactional
    public Map<String, Object> linkReplacementPo(UUID id, UUID replacementPoId, String updatedBy) {
        SupplierReturn sr = supplierReturnRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("SupplierReturn not found: " + id));
        PurchaseOrder replacementPo = purchaseOrderRepository.findById(replacementPoId)
            .orElseThrow(() -> new IllegalArgumentException("Replacement PurchaseOrder not found: " + replacementPoId));

        String oldStatus = sr.getStatus();
        sr.setStatus("REPLACEMENT_RECEIVED");
        sr.setReplacementPo(replacementPo);
        supplierReturnRepository.save(sr);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(updatedBy)
            .action("LINK_REPLACEMENT_PO")
            .entityType("SupplierReturn")
            .entityId(id)
            .entityCode(sr.getCode())
            .oldStatus(oldStatus)
            .newStatus("REPLACEMENT_RECEIVED")
            .note("replacementPoId=" + replacementPoId)
            .createdAt(OffsetDateTime.now())
            .build());

        return toDetailMap(sr);
    }

    @Transactional
    public Map<String, Object> markWrittenOff(UUID id, String updatedBy) {
        SupplierReturn sr = supplierReturnRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("SupplierReturn not found: " + id));
        String oldStatus = sr.getStatus();
        sr.setStatus("WRITTEN_OFF");
        supplierReturnRepository.save(sr);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(updatedBy)
            .action("WRITE_OFF_SUPPLIER_RETURN")
            .entityType("SupplierReturn")
            .entityId(id)
            .entityCode(sr.getCode())
            .oldStatus(oldStatus)
            .newStatus("WRITTEN_OFF")
            .createdAt(OffsetDateTime.now())
            .build());

        return toMap(sr);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listReturns(String status, UUID supplierId) {
        List<SupplierReturn> list;
        if (supplierId != null && status != null) {
            list = supplierReturnRepository.findAllBySupplierIdAndStatusOrderByCreatedAtDesc(supplierId, status);
        } else if (supplierId != null) {
            list = supplierReturnRepository.findAllBySupplierIdOrderByCreatedAtDesc(supplierId);
        } else if (status != null) {
            list = supplierReturnRepository.findAllByStatusOrderByCreatedAtDesc(status);
        } else {
            list = supplierReturnRepository.findAll().stream()
                .sorted(Comparator.comparing(SupplierReturn::getCreatedAt).reversed())
                .collect(Collectors.toList());
        }
        return list.stream().map(this::toMap).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getReturn(UUID id) {
        SupplierReturn sr = supplierReturnRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("SupplierReturn not found: " + id));
        return toDetailMap(sr);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Map<String, Object> toMap(SupplierReturn sr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", sr.getId());
        m.put("code", sr.getCode());
        m.put("supplierId", sr.getSupplier().getId());
        m.put("supplierName", sr.getSupplier().getName());
        m.put("originalPoId", sr.getOriginalPo().getId());
        m.put("originalPoCode", sr.getOriginalPo().getCode());
        m.put("returnDate", sr.getReturnDate().toString());
        m.put("status", sr.getStatus());
        m.put("reason", sr.getReason());
        m.put("createdBy", sr.getCreatedBy());
        m.put("createdAt", sr.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> toDetailMap(SupplierReturn sr) {
        Map<String, Object> m = toMap(sr);
        m.put("note", sr.getNote());
        m.put("writeOffId", sr.getWriteOff() != null ? sr.getWriteOff().getId() : null);
        m.put("writeOffCode", sr.getWriteOff() != null ? sr.getWriteOff().getCode() : null);
        m.put("replacementPoId", sr.getReplacementPo() != null ? sr.getReplacementPo().getId() : null);
        m.put("replacementPoCode", sr.getReplacementPo() != null ? sr.getReplacementPo().getCode() : null);
        return m;
    }
}
