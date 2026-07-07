package com.bakery.api.modules.production.services;

import com.bakery.api.framework.*;
import com.bakery.api.framework.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.bakery.api.framework.services.CodeSequenceService;
import com.bakery.api.modules.production.entities.ProductionPlan;
import com.bakery.api.modules.production.entities.ProductionPlanLine;
import com.bakery.api.modules.production.entities.ProductionRequest;
import com.bakery.api.modules.production.repositories.ProductionPlanRepository;
import com.bakery.api.modules.production.repositories.ProductionRequestRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionPlanService {

    private final ProductionPlanRepository productionPlanRepository;
    private final ProductionRequestRepository productionRequestRepository;
    private final ActivityLogRepository activityLogRepository;
    private final CodeSequenceService codeSequenceService;

    // Records for request bodies
    public record CreatePlanRequest(LocalDate planDate, String note, String createdBy,
                                    List<PlanLineRequest> lines) {}
    public record PlanLineRequest(UUID productId, BigDecimal qtyPlanned, String note) {}
    public record AdjustLineRequest(BigDecimal qtyAdjusted, String note) {}

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPlans(String status, LocalDate from, LocalDate to) {
        List<ProductionPlan> plans;
        if (status != null) {
            plans = productionPlanRepository.findAllByStatusOrderByPlanDateDesc(status);
        } else {
            plans = productionPlanRepository.findAll().stream()
                .sorted(Comparator.comparing(ProductionPlan::getPlanDate).reversed())
                .collect(Collectors.toList());
        }

        if (from != null) {
            plans = plans.stream()
                .filter(p -> !p.getPlanDate().isBefore(from))
                .collect(Collectors.toList());
        }
        if (to != null) {
            plans = plans.stream()
                .filter(p -> !p.getPlanDate().isAfter(to))
                .collect(Collectors.toList());
        }

        return plans.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("planDate", p.getPlanDate().toString());
            m.put("status", p.getStatus());
            m.put("lineCount", p.getLines().size());
            m.put("note", p.getNote());
            m.put("approvedBy", p.getApprovedBy());
            m.put("approvedAt", p.getApprovedAt() != null ? p.getApprovedAt().toString() : null);
            m.put("createdBy", p.getCreatedBy());
            m.put("createdAt", p.getCreatedAt().toString());
            return m;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPlanWithLines(UUID id) {
        ProductionPlan plan = productionPlanRepository.findByIdWithLines(id)
            .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + id));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", plan.getId());
        result.put("planDate", plan.getPlanDate().toString());
        result.put("status", plan.getStatus());
        result.put("note", plan.getNote());
        result.put("approvedBy", plan.getApprovedBy());
        result.put("approvedAt", plan.getApprovedAt() != null ? plan.getApprovedAt().toString() : null);
        result.put("rejectionReason", plan.getRejectionReason());
        result.put("createdBy", plan.getCreatedBy());
        result.put("createdAt", plan.getCreatedAt().toString());
        result.put("lines", plan.getLines().stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("productId", l.getProduct().getId());
            m.put("productCode", l.getProduct().getCode());
            m.put("productName", l.getProduct().getName());
            m.put("qtyPlanned", l.getQtyPlanned());
            m.put("qtyAdjusted", l.getQtyAdjusted());
            m.put("effectiveQty", l.getEffectiveQty());
            m.put("note", l.getNote());
            return m;
        }).collect(Collectors.toList()));

        return result;
    }

    @Transactional
    public Map<String, Object> approvePlan(UUID id, String approvedBy) {
        ProductionPlan plan = productionPlanRepository.findByIdWithLines(id)
            .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + id));
        if (!"PENDING".equals(plan.getStatus())) {
            throw new IllegalStateException("Plan is not PENDING, current status: " + plan.getStatus());
        }
        plan.setStatus("APPROVED");
        plan.setApprovedBy(approvedBy);
        plan.setApprovedAt(OffsetDateTime.now());
        productionPlanRepository.save(plan);

        // Create one ProductionRequest per line
        LocalDate today = LocalDate.now();
        for (ProductionPlanLine line : plan.getLines()) {
            ProductionRequest req = ProductionRequest.builder()
                .code(codeSequenceService.nextProductionRequestCode(today))
                .requestType("DAILY")
                .status("PENDING")
                .plan(plan)
                .product(line.getProduct())
                .qtyPlanned(line.getEffectiveQty())
                .createdAt(OffsetDateTime.now())
                .requestedBy(approvedBy)
                .build();
            productionRequestRepository.save(req);
        }

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(approvedBy)
            .action("APPROVE_PLAN")
            .entityType("ProductionPlan")
            .entityId(plan.getId())
            .entityCode(plan.getPlanDate().toString())
            .oldStatus("PENDING")
            .newStatus("APPROVED")
            .createdAt(OffsetDateTime.now())
            .build());

        log.info("Approved ProductionPlan {} by {}, created {} requests",
            plan.getPlanDate(), approvedBy, plan.getLines().size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", plan.getId());
        result.put("planDate", plan.getPlanDate());
        result.put("status", plan.getStatus());
        result.put("approvedBy", plan.getApprovedBy());
        result.put("requestsCreated", plan.getLines().size());
        return result;
    }

    @Transactional
    public Map<String, Object> rejectPlan(UUID id, String rejectedBy, String reason) {
        ProductionPlan plan = productionPlanRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + id));
        if (!"PENDING".equals(plan.getStatus())) {
            throw new IllegalStateException("Plan is not PENDING, current status: " + plan.getStatus());
        }
        plan.setStatus("REJECTED");
        plan.setRejectionReason(reason);
        productionPlanRepository.save(plan);

        activityLogRepository.save(ActivityLog.builder()
            .performedBy(rejectedBy)
            .action("REJECT_PLAN")
            .entityType("ProductionPlan")
            .entityId(plan.getId())
            .entityCode(plan.getPlanDate().toString())
            .oldStatus("PENDING")
            .newStatus("REJECTED")
            .note(reason)
            .createdAt(OffsetDateTime.now())
            .build());

        log.info("Rejected ProductionPlan {} by {}", plan.getPlanDate(), rejectedBy);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", plan.getId());
        result.put("status", "REJECTED");
        result.put("rejectionReason", reason);
        return result;
    }

    @Transactional
    public Map<String, Object> adjustPlanLine(UUID planId, UUID lineId, BigDecimal qtyAdjusted, String note) {
        ProductionPlan plan = productionPlanRepository.findByIdWithLines(planId)
            .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planId));
        ProductionPlanLine line = plan.getLines().stream()
            .filter(l -> l.getId().equals(lineId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Line not found: " + lineId));
        line.setQtyAdjusted(qtyAdjusted);
        if (note != null) line.setNote(note);
        productionPlanRepository.save(plan);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lineId", line.getId());
        result.put("qtyPlanned", line.getQtyPlanned());
        result.put("qtyAdjusted", qtyAdjusted);
        result.put("effectiveQty", line.getEffectiveQty());
        return result;
    }
}
