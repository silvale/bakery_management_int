package com.bakery.api.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.bakery.framework.dto.BaseResponse;
import com.bakery.framework.metadata.ReferenceValue;
import com.bakery.framework.entity.InventoryRequestType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InventoryRequestResponse extends BaseResponse {

    private String code;
    private InventoryRequestType requestType;
    private LocalDate requestDate;
    private LocalDate expectedDeliveryDate;
    private ReferenceValue sourceWarehouse;
    private ReferenceValue targetWarehouse;
    private ReferenceValue supplier;
    private String note;
    private List<InventoryRequestLineResponse> lines;

    /** DELIVERED | PENDING | OVERDUE | null (REJECTED hoặc không phải PURCHASE). Tính khi trả, không lưu. */
    private String deliveryStatus;
    /** UNPAID | PAID | null (không phải PURCHASE). */
    private String paymentStatus;
    private Instant paidAt;
    /** Username người xác nhận thanh toán. */
    private String paidBy;
    /** 11/D6: user không xem được giá NCC của phiếu → mọi trường giá là null. */
    private boolean priceHidden;
    /** 11/BE-6: phiếu nhập có dòng chưa có đơn giá (chưa có trong bảng giá NCC) — để Admin bổ sung. */
    private boolean priceMissing;
    /**
     * 15/D2: user hiện tại hủy được thanh toán phiếu này: Admin + phiếu PAID + còn trong 30 ngày kể từ lúc thanh toán.
     * Quy tắc nằm ở BE; FE chỉ đọc cờ này, không tự tính ngày.
     */
    @com.fasterxml.jackson.annotation.JsonProperty("canUnpay")
    private boolean canUnpay;
    /** 11/BE-4: phiếu do chính user hiện tại tạo. */
    private boolean createdByMe;
    /** 11/BE-4: user hiện tại sửa/xóa được phiếu này (đã tính quyền, kho được gán, người tạo và trạng thái). */
    private boolean canEdit;
    /** Ngày thực nhận và người xác nhận (06/BE-1); null khi chưa nhận hoặc dữ liệu cũ không có người nhận. */
    private LocalDate receivedDate;
    private String receivedBy;
    /** Tổng line.totalCost; null nếu không phải PURCHASE. */
    private BigDecimal totalAmount;

    /** 24: phiếu bảng tạm (PICK / RETURN / WRITE_OFF). */
    private ProductionRequestRef productionRequest;
    private String pickReason;
    private java.util.UUID sessionId;

    public record ProductionRequestRef(java.util.UUID id, String code) {}
}
