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
    private Integer batchWeightGrams;
    private String note;
    private boolean active;
    private List<GroupItemResponse> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class GroupItemResponse {
        private UUID id;
        private ReferenceValue item;
        private BigDecimal gramsPerUnit;
        private int sortOrder;

        public static GroupItemResponse from(ProductionGroupItem gi) {
            GroupItemResponse r = new GroupItemResponse();
            r.setId(gi.getId());
            r.setGramsPerUnit(gi.getGramsPerUnit());
            r.setSortOrder(gi.getSortOrder());
            if (gi.getItem() != null) {
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
