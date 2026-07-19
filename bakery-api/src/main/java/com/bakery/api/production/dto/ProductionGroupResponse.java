package com.bakery.api.production.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.bakery.api.production.entity.ProductionGroup;
import com.bakery.api.production.entity.ProductionGroupItem;
import com.bakery.framework.dto.BaseResponse;
import com.bakery.framework.metadata.ReferenceValue;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductionGroupResponse extends BaseResponse {
    private String code;
    private String name;
    private String groupType;
    private ReferenceValue itemGroup;
    private Integer targetWeekday;
    private Integer targetWeekend;
    private Integer thresholdPercent;
    /** Base recipe cho nhóm FREE_GROUP: id + tên để hiển thị trên UI. */
    private UUID baseRecipeId;
    private String baseRecipeName;
    private Integer batchWeightGrams;
    private String note;
    private boolean active;
    private List<GroupItemResponse> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class GroupItemResponse {
        private UUID id;
        private UUID itemId;   // UUID của item — dùng để call API
        private ReferenceValue item;
        private BigDecimal gramsPerUnit;
        private int sortOrder;

        public static GroupItemResponse from(ProductionGroupItem gi) {
            GroupItemResponse r = new GroupItemResponse();
            r.setId(gi.getId());
            r.setGramsPerUnit(gi.getGramsPerUnit());
            r.setSortOrder(gi.getSortOrder());
            if (gi.getItem() != null) {
                r.setItemId(gi.getItem().getId());
                r.setItem(new ReferenceValue(gi.getItem().getCode(), gi.getItem().getName()));
            }
            return r;
        }
    }

    public static ProductionGroupResponse from(ProductionGroup e) {
        ProductionGroupResponse r = new ProductionGroupResponse();
        r.applyFrom(e);
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setGroupType(e.getGroupType());
        r.setTargetWeekday(e.getTargetWeekday());
        r.setTargetWeekend(e.getTargetWeekend());
        r.setThresholdPercent(e.getThresholdPercent());
        if (e.getBaseRecipe() != null) {
            r.setBaseRecipeId(e.getBaseRecipe().getId());
            String rName = null;
            if (e.getBaseRecipe().getProduct() != null) rName = e.getBaseRecipe().getProduct().getName();
            else if (e.getBaseRecipe().getSemiProduct() != null) rName = e.getBaseRecipe().getSemiProduct().getName();
            r.setBaseRecipeName(rName != null ? rName : "Recipe #" + e.getBaseRecipe().getId().toString().substring(0, 8));
        }
        r.setBatchWeightGrams(e.getBatchWeightGrams());
        r.setNote(e.getNote());
        r.setActive(e.isActive());
        if (e.getItemGroup() != null) {
            r.setItemGroup(new ReferenceValue(e.getItemGroup().getCode(), e.getItemGroup().getName()));
        }
        r.setItems(e.getItems().stream().map(GroupItemResponse::from).toList());
        return r;
    }
}
