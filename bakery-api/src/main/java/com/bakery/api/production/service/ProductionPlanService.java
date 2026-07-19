package com.bakery.api.production.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bakery.api.production.dto.ProductionPlanAdjustRequest;
import com.bakery.api.production.dto.ProductionPlanResponse;
import com.bakery.api.production.entity.ProductionPlan;
import com.bakery.api.production.entity.ProductionPlanLine;
import com.bakery.api.production.repository.ProductionPlanLineRepository;
import com.bakery.api.production.repository.ProductionPlanRepository;
import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.security.BakeryActorResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý vòng đời ProductionPlan: xem, điều chỉnh, approve, reject.
 *
 * <p>Bếp chỉ được xem plan có status = APPROVED.
 * Manager thấy cả DRAFT và APPROVED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionPlanService {

    private final ProductionPlanRepository planRepository;
    private final ProductionPlanLineRepository lineRepository;
    private final BakeryActorResolver actorResolver;
    private final ProductionRequestService productionRequestService;
    private final ProductionIngredientService ingredientService;

    /** Manager xem kế hoạch theo ngày (DRAFT hoặc APPROVED). */
    @Transactional(readOnly = true)
    public ProductionPlanResponse findByDate(LocalDate date) {
        ProductionPlan plan = planRepository.findByPlanDate(date)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionPlan for date " + date));
        return ProductionPlanResponse.from(plan);
    }

    /** Bếp xem danh sách kế hoạch đã APPROVED. */
    @Transactional(readOnly = true)
    public List<ProductionPlanResponse> findApproved() {
        return planRepository.findByApprovalStatusOrderByPlanDateDesc(ApprovalStatus.APPROVED).stream()
                .map(ProductionPlanResponse::from)
                .toList();
    }

    /** Manager xem danh sách kế hoạch DRAFT chờ duyệt. */
    @Transactional(readOnly = true)
    public List<ProductionPlanResponse> findDraft() {
        return planRepository.findByApprovalStatusOrderByPlanDateDesc(ApprovalStatus.DRAFT).stream()
                .map(ProductionPlanResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductionPlanResponse findById(UUID id) {
        return ProductionPlanResponse.from(getById(id));
    }

    /**
     * Manager điều chỉnh số lượng khi plan còn DRAFT.
     * Chỉ cập nhật {@code adjustedQty} — {@code suggestedQty} không thay đổi.
     */
    @Transactional
    public ProductionPlanResponse adjust(UUID planId, ProductionPlanAdjustRequest req) {
        ProductionPlan plan = getById(planId);
        assertDraft(plan);

        Map<UUID, Integer> adjustMap = req.lines().stream()
                .collect(Collectors.toMap(
                        ProductionPlanAdjustRequest.LineAdjust::lineId,
                        ProductionPlanAdjustRequest.LineAdjust::adjustedQty));

        for (ProductionPlanLine line : plan.getLines()) {
            if (adjustMap.containsKey(line.getId())) {
                line.setAdjustedQty(adjustMap.get(line.getId()));
                lineRepository.save(line);
            }
        }

        return ProductionPlanResponse.from(plan);
    }

    /**
     * Manager approve → bếp thấy kế hoạch.
     * Sau khi APPROVED không thể chỉnh sửa nữa.
     */
    @Transactional
    public ProductionPlanResponse approve(UUID planId) {
        ProductionPlan plan = getById(planId);
        assertDraft(plan);

        // Force-init lines trong cùng transaction TRƯỚC khi save
        // Tránh trường hợp saved entity có lines chưa được load → generateFromPlan bỏ qua
        plan.getLines().size();

        plan.setApprovalStatus(ApprovalStatus.APPROVED);
        plan.setApprovedBy(actorResolver.currentUserId());
        ProductionPlan saved = planRepository.save(plan);

        // Tự động tạo phiếu SX DAILY theo từng ItemGroup
        int requestCount = productionRequestService.generateFromPlan(saved).size();
        log.info("Approve plan {} → tạo {} phiếu SX DAILY", saved.getPlanDate(), requestCount);

        // Tự động tạo phiếu TRANSFER MAIN→KITCHEN (PENDING_APPROVAL) dựa trên BOM
        try {
            var transfer = ingredientService.generateTransferRequest(saved.getId());
            log.info("Approve plan {} → tạo phiếu xuất kho {} ({} NL)",
                    saved.getPlanDate(), transfer.getCode(),
                    transfer.getLines() != null ? transfer.getLines().size() : 0);
        } catch (IllegalStateException ex) {
            // BOM chưa setup hoặc không có NL cần xuất — không block approve
            log.warn("Approve plan {} → bỏ qua tạo phiếu TRANSFER: {}", saved.getPlanDate(), ex.getMessage());
        }

        return ProductionPlanResponse.from(saved);
    }

    /**
     * Tạo lại phiếu SX từ plan đã APPROVED (dùng khi approve xong nhưng PRs chưa được tạo).
     * Idempotent: không tạo PR mới nếu đã tồn tại PR cho cùng ngày + itemGroup.
     */
    @Transactional
    public int generateRequestsFromApprovedPlan(UUID planId) {
        ProductionPlan plan = getById(planId);
        if (plan.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new IllegalStateException(
                    "Chỉ có thể generate PRs từ plan APPROVED. Hiện tại: " + plan.getApprovalStatus());
        }
        plan.getLines().size(); // force-init
        int count = productionRequestService.generateFromPlan(plan).size();
        log.info("generateRequestsFromApprovedPlan: plan {} → tạo {} phiếu SX", plan.getPlanDate(), count);
        return count;
    }

    /**
     * Manager reject DRAFT — có thể tạo lại bằng cách gọi finalize lại báo cáo ngày
     * hoặc endpoint generate thủ công.
     */
    @Transactional
    public ProductionPlanResponse reject(UUID planId, String reason) {
        ProductionPlan plan = getById(planId);
        assertDraft(plan);

        plan.setApprovalStatus(ApprovalStatus.REJECTED);
        plan.setRejectedReason(reason);
        return ProductionPlanResponse.from(planRepository.save(plan));
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private ProductionPlan getById(UUID id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionPlan", id));
    }

    private void assertDraft(ProductionPlan plan) {
        if (plan.getApprovalStatus() != ApprovalStatus.DRAFT) {
            throw new IllegalStateException(
                    "Kế hoạch " + plan.getPlanDate() + " không còn ở trạng thái DRAFT.");
        }
    }
}
