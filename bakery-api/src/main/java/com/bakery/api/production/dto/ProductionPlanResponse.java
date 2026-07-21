package com.bakery.api.production.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.bakery.api.production.entity.ProductionPlan;
import com.bakery.api.production.entity.ProductionPlanGroup;
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

    /**
     * Thông tin plannedQty theo từng group (FREE_GROUP + BATCH_FORMULA).
     * FE dùng để hiển thị num_batches input và validate BATCH_FORMULA weight.
     */
    private List<PlanGroupSummary> planGroups;

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
        /** BATCH_FORMULA: số lượng default mỗi cối — FE dùng để tính adjustedQty = defaultQtyPerBatch × numBatches. */
        private Integer defaultQtyPerBatch;
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
            r.setDefaultQtyPerBatch(line.getDefaultQtyPerBatch());
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

    /** Summary cho 1 group trong plan — FE dùng để render section BATCH_FORMULA / FREE_GROUP. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class PlanGroupSummary {
        private UUID id;
        private UUID groupId;
        private String groupCode;
        private String groupName;
        /** FREE_GROUP | BATCH_FORMULA */
        private String groupType;
        /** FREE_GROUP: target qty; BATCH_FORMULA: số cối. */
        private int plannedQty;
        /** Trọng lượng 1 cối (gram) — chỉ có giá trị với BATCH_FORMULA. */
        private Integer batchWeightGrams;

        public static PlanGroupSummary from(ProductionPlanGroup ppg) {
            PlanGroupSummary s = new PlanGroupSummary();
            s.setId(ppg.getId());
            s.setPlannedQty(ppg.getPlannedQty());
            if (ppg.getGroup() != null) {
                s.setGroupId(ppg.getGroup().getId());
                s.setGroupCode(ppg.getGroup().getCode());
                s.setGroupName(ppg.getGroup().getName());
                s.setGroupType(ppg.getGroup().getGroupType());
                s.setBatchWeightGrams(ppg.getGroup().getBatchWeightGrams());
            }
            return s;
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
