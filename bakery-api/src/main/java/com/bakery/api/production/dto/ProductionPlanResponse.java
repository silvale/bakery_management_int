package com.bakery.api.production.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.bakery.api.production.entity.ProductionPlan;
import com.bakery.api.production.entity.ProductionPlanLine;
import com.bakery.framework.dto.BaseResponse;
import com.bakery.framework.metadata.ReferenceValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductionPlanResponse extends BaseResponse {

    private LocalDate planDate;
    private String dayType;
    /** DRAFT | APPROVED | REJECTED */
    private String status;
    private String approvedBy;

    /** Danh sách items nhóm theo planType + group để FE render từng section. */
    private List<PlanLineResponse> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PlanLineResponse {
        private UUID id;
        private ReferenceValue item;
        /** SIMPLE | FREE_GROUP | BATCH_FORMULA */
        private String planType;
        private ReferenceValue group;
        private BigDecimal qtyRemaining;
        private Integer suggestedQty;
        private Integer adjustedQty;
        /** Số lượng hiệu lực: adjustedQty nếu có, ngược lại suggestedQty. */
        private Integer effectiveQty;
        private BigDecimal gramsPerUnit;
        private String ruleNote;
        private int sortOrder;

        public static PlanLineResponse from(ProductionPlanLine line) {
            PlanLineResponse r = new PlanLineResponse();
            r.setId(line.getId());
            r.setPlanType(line.getPlanType());
            r.setQtyRemaining(line.getQtyRemaining());
            r.setSuggestedQty(line.getSuggestedQty());
            r.setAdjustedQty(line.getAdjustedQty());
            r.setEffectiveQty(line.getEffectiveQty());
            r.setGramsPerUnit(line.getGramsPerUnit());
            r.setRuleNote(line.getRuleNote());
            r.setSortOrder(line.getSortOrder());
            if (line.getItem() != null) {
                r.setItem(new ReferenceValue(line.getItem().getCode(), line.getItem().getName()));
            }
            if (line.getGroup() != null) {
                r.setGroup(new ReferenceValue(line.getGroup().getCode(), line.getGroup().getName()));
            }
            return r;
        }
    }

    public static ProductionPlanResponse from(ProductionPlan plan) {
        ProductionPlanResponse r = new ProductionPlanResponse();
        r.applyFrom(plan);
        r.setPlanDate(plan.getPlanDate());
        r.setDayType(plan.getDayType() != null ? plan.getDayType().name() : null);
        r.setStatus(plan.getApprovalStatus() != null ? plan.getApprovalStatus().name() : null);
        r.setApprovedBy(plan.getApprovedBy());
        r.setLines(plan.getLines().stream().map(PlanLineResponse::from).toList());
        return r;
    }
}
