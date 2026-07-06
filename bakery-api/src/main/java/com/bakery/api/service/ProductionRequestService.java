package com.bakery.api.service;

import com.bakery.common.entity.*;
import com.bakery.common.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionRequestService {

    private final ProductionRequestRepository productionRequestRepository;
    private final ProductRepository productRepository;
    private final ActivityLogRepository activityLogRepository;
    private final CodeSequenceService codeSequenceService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRequests(String status, String requestType, LocalDate date) {
        List<ProductionRequest> requests;

        if (status != null && requestType != null) {
            requests = productionRequestRepository
                .findAllByRequestTypeAndStatusOrderByCreatedAtDesc(requestType, status);
        } else if (status != null) {
            requests = productionRequestRepository.findAllByStatusOrderByCreatedAtDesc(status);
        } else if (requestType != null) {
            // no combined method without status — filter after load
            requests = productionRequestRepository.findAll().stream()
                .filter(r -> requestType.equals(r.getRequestType()))
                .sorted(Comparator.comparing(ProductionRequest::getCreatedAt).reversed())
                .collect(Collectors.toList());
        } else {
            requests = productionRequestRepository.findAll().stream()
                .sorted(Comparator.comparing(ProductionRequest::getCreatedAt).reversed())
                .collect(Collectors.toList());
        }

        if (date != null) {
            OffsetDateTime start = date.atStartOfDay().atOffset(ZoneOffset.UTC);
            OffsetDateTime end = date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
            requests = requests.stream()
                .filter(r -> !r.getCreatedAt().isBefore(start) && r.getCreatedAt().isBefore(end))
                .collect(Collectors.toList());
        }

        return requests.stream().map(this::toMap).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRequest(UUID id) {
        ProductionRequest req = productionRequestRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("ProductionRequest not found: " + id));
        return toDetailMap(req);
    }

    @Transactional
    public Map<String, Object> createAdHocRequest(UUID productId, UUID recipeId, String requestType,
                                                   BigDecimal qtyPlanned, UUID customerOrderId,
                                                   String createdBy, String note) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        LocalDate today = LocalDate.now();
        String code = codeSequenceService.nextProductionRequestCode(today);

        ProductionRequest req = ProductionRequest.builder()
            .code(code)
            .requestType(requestType != null ? requestType : "URGENT")
            .status("PENDING")
            .product(product)
            .qtyPlanned(qtyPlanned)
            .note(note)
            .requestedBy(createdBy != null ? createdBy : "system")
            .createdAt(OffsetDateTime.now())
            .build();

        productionRequestRepository.save(req);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(createdBy != null ? createdBy : "system")
            .action("CREATE_PRODUCTION_REQUEST")
            .entityType("ProductionRequest")
            .entityId(req.getId())
            .entityCode(req.getCode())
            .newStatus("PENDING")
            .createdAt(OffsetDateTime.now())
            .build());

        log.info("Created ad-hoc ProductionRequest {} type={}", code, requestType);
        return toDetailMap(req);
    }

    @Transactional
    public Map<String, Object> approveRequest(UUID id, String approvedBy) {
        ProductionRequest req = productionRequestRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("ProductionRequest not found: " + id));
        if (!"PENDING".equals(req.getStatus())) {
            throw new IllegalStateException("Request is not PENDING, current status: " + req.getStatus());
        }
        req.setStatus("APPROVED");
        req.setApprovedBy(approvedBy);
        req.setApprovedAt(OffsetDateTime.now());
        productionRequestRepository.save(req);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(approvedBy)
            .action("APPROVE_PRODUCTION_REQUEST")
            .entityType("ProductionRequest")
            .entityId(req.getId())
            .entityCode(req.getCode())
            .oldStatus("PENDING")
            .newStatus("APPROVED")
            .createdAt(OffsetDateTime.now())
            .build());

        return toMap(req);
    }

    @Transactional
    public Map<String, Object> rejectRequest(UUID id, String rejectedBy, String reason) {
        ProductionRequest req = productionRequestRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("ProductionRequest not found: " + id));
        if (!"PENDING".equals(req.getStatus())) {
            throw new IllegalStateException("Request is not PENDING, current status: " + req.getStatus());
        }
        req.setStatus("REJECTED");
        req.setApprovedBy(rejectedBy);
        req.setApprovedAt(OffsetDateTime.now());
        req.setRejectionReason(reason);
        productionRequestRepository.save(req);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(rejectedBy)
            .action("REJECT_PRODUCTION_REQUEST")
            .entityType("ProductionRequest")
            .entityId(req.getId())
            .entityCode(req.getCode())
            .oldStatus("PENDING")
            .newStatus("REJECTED")
            .note(reason)
            .createdAt(OffsetDateTime.now())
            .build());

        return toMap(req);
    }

    @Transactional
    public Map<String, Object> startProduction(UUID id, String startedBy) {
        ProductionRequest req = productionRequestRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("ProductionRequest not found: " + id));
        if (!"APPROVED".equals(req.getStatus())) {
            throw new IllegalStateException("Request is not APPROVED, current status: " + req.getStatus());
        }
        req.setStatus("IN_PRODUCTION");
        productionRequestRepository.save(req);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(startedBy)
            .action("START_PRODUCTION")
            .entityType("ProductionRequest")
            .entityId(req.getId())
            .entityCode(req.getCode())
            .oldStatus("APPROVED")
            .newStatus("IN_PRODUCTION")
            .createdAt(OffsetDateTime.now())
            .build());

        return toMap(req);
    }

    @Transactional
    public Map<String, Object> completeRequest(UUID id, BigDecimal qtyActual, String varianceReason,
                                               String completedBy) {
        ProductionRequest req = productionRequestRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("ProductionRequest not found: " + id));
        if (!"IN_PRODUCTION".equals(req.getStatus())) {
            throw new IllegalStateException("Request is not IN_PRODUCTION, current status: " + req.getStatus());
        }
        req.setStatus("COMPLETED");
        req.setQtyActual(qtyActual);
        req.setVarianceReason(varianceReason);
        productionRequestRepository.save(req);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(completedBy)
            .action("COMPLETE_PRODUCTION")
            .entityType("ProductionRequest")
            .entityId(req.getId())
            .entityCode(req.getCode())
            .oldStatus("IN_PRODUCTION")
            .newStatus("COMPLETED")
            .note(varianceReason)
            .createdAt(OffsetDateTime.now())
            .build());

        return toMap(req);
    }

    @Transactional
    public Map<String, Object> cancelRequest(UUID id, String cancelledBy, String reason) {
        ProductionRequest req = productionRequestRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("ProductionRequest not found: " + id));
        String oldStatus = req.getStatus();
        req.setStatus("CANCELLED");
        req.setRejectionReason(reason);
        productionRequestRepository.save(req);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(cancelledBy)
            .action("CANCEL_PRODUCTION_REQUEST")
            .entityType("ProductionRequest")
            .entityId(req.getId())
            .entityCode(req.getCode())
            .oldStatus(oldStatus)
            .newStatus("CANCELLED")
            .note(reason)
            .createdAt(OffsetDateTime.now())
            .build());

        return toMap(req);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Map<String, Object> toMap(ProductionRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", req.getId());
        m.put("code", req.getCode());
        m.put("requestType", req.getRequestType());
        m.put("status", req.getStatus());
        m.put("productId", req.getProduct().getId());
        m.put("productCode", req.getProduct().getCode());
        m.put("productName", req.getProduct().getName());
        m.put("qtyPlanned", req.getQtyPlanned());
        m.put("qtyActual", req.getQtyActual());
        m.put("requestedBy", req.getRequestedBy());
        m.put("approvedBy", req.getApprovedBy());
        m.put("createdAt", req.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> toDetailMap(ProductionRequest req) {
        Map<String, Object> m = toMap(req);
        m.put("note", req.getNote());
        m.put("varianceReason", req.getVarianceReason());
        m.put("rejectionReason", req.getRejectionReason());
        m.put("approvedAt", req.getApprovedAt() != null ? req.getApprovedAt().toString() : null);
        m.put("planId", req.getPlan() != null ? req.getPlan().getId() : null);
        m.put("priceOverride", req.getPriceOverride());
        return m;
    }
}
