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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý vòng đời ProductionPlan: xem, điều chỉnh, approve, reject.
 *
 * <p>Bếp chỉ được xem plan có status = APPROVED.
 * Manager thấy cả DRAFT và APPROVED.
 */
@Service
@RequiredArgsConstructor
public class ProductionPlanService {

    private final ProductionPlanRepository planRepository;
    private final ProductionPlanLineRepository lineRepository;
    private final BakeryActorResolver actorResolver;

    /** Manager xem kế hoạch theo ngày (DRAFT hoặc APPROVED). */
    public ProductionPlanResponse findByDate(LocalDate date) {
        ProductionPlan plan = planRepository.findByPlanDate(date)
                .orElseThrow(() -> new ResourceNotFoundException("ProductionPlan for date " + date));
        return ProductionPlanResponse.from(plan);
    }

    /** Bếp xem danh sách kế hoạch đã APPROVED. */
    public List<ProductionPlanResponse> findApproved() {
        return planRepository.findByApprovalStatusOrderByPlanDateDesc(ApprovalStatus.APPROVED).stream()
                .map(ProductionPlanResponse::from)
                .toList();
    }

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

        plan.setApprovalStatus(ApprovalStatus.APPROVED);
        plan.setApprovedBy(actorResolver.currentUserId());
        return ProductionPlanResponse.from(planRepository.save(plan));
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
