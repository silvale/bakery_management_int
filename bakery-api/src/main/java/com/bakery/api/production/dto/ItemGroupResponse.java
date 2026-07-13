package com.bakery.api.production.dto;

import com.bakery.api.production.entity.ItemGroup;
import com.bakery.framework.dto.BaseResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ItemGroupResponse extends BaseResponse {
    private String code;
    private String name;
    private int sortOrder;

    public static ItemGroupResponse from(ItemGroup e) {
        ItemGroupResponse r = new ItemGroupResponse();
        r.applyFrom(e);
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setSortOrder(e.getSortOrder());
        return r;
    }
}
