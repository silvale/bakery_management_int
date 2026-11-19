package com.bakery.api.master.dto;

import java.util.List;
import java.util.UUID;

import com.bakery.framework.metadata.ReferenceValue;
import lombok.Getter;
import lombok.Setter;

/**
 * 12/BE-1: mặt hàng thuộc một NCC, để lọc dropdown ở phiếu nhập. Cố ý KHÔNG có field giá nào (12/D3, 11/BE-5).
 * {@code matchedBy}: DEFAULT (NCC mặc định của hàng) | PRICE_LIST (có trong bảng giá của NCC) | BOTH.
 */
@Getter
@Setter
public class ItemBySupplierResponse {
    private UUID id;
    private String code;
    private String name;
    private String unit;
    private String itemType;
    private String imageUrl;
    private ReferenceValue itemGroup;
    private ReferenceValue defaultSupplier;
    private List<ItemResponse.PackagingResponse> packagings = List.of();
    private String matchedBy;
}
