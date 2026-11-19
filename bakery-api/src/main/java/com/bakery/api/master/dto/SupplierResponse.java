package com.bakery.api.master.dto;

import com.bakery.framework.dto.BaseResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SupplierResponse extends BaseResponse {

    private String code;
    private String name;
    private String contactName;
    private String phone;
    private String email;
    private String address;
    /** "Mua lẻ / trả ngay" (chợ) — JSON {@code isCashPurchase} (contract 11 §7). */
    @com.fasterxml.jackson.annotation.JsonProperty("isCashPurchase")
    private boolean cashPurchase;
}
