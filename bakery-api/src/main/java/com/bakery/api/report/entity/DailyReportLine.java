package com.bakery.api.report.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.bakery.api.master.entity.Item;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Chi tiết báo cáo ngày theo từng sản phẩm.
 * Snapshot số liệu tại thời điểm FINALIZE — giữ nguyên dù data gốc thay đổi sau này.
 */
@Getter
@Setter
@Entity
@Table(name = "daily_report_line",
        uniqueConstraints = @UniqueConstraint(columnNames = {"daily_report_id", "item_id"}))
public class DailyReportLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_report_id", nullable = false)
    private DailyReport dailyReport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    /** Tổng qtyProduced (sau tất cả adjustment đã approve) */
    @Column(name = "qty_produced", precision = 10, scale = 3)
    private BigDecimal qtyProduced;

    /** Tổng qtyReceived shop xác nhận */
    @Column(name = "qty_received", precision = 10, scale = 3)
    private BigDecimal qtyReceived;

    /** Nhân viên nhập tay — số bánh còn lại cuối ngày tại cửa hàng */
    @Column(name = "qty_remaining_actual", precision = 10, scale = 3)
    private BigDecimal qtyRemainingActual;

    /** qty_received - qty_remaining_actual */
    @Column(name = "qty_sold_implied", precision = 10, scale = 3)
    private BigDecimal qtySoldImplied;

    /** Từ pos_daily_sale (sau map EX_CODE) */
    @Column(name = "qty_sold_pos", precision = 10, scale = 3)
    private BigDecimal qtySoldPos;

    /** qty_produced - qty_received: bếp ra nhiều hơn/ít hơn shop nhận */
    @Column(name = "discrepancy_kitchen", precision = 10, scale = 3)
    private BigDecimal discrepancyKitchen;

    /** qty_sold_implied - qty_sold_pos: shop báo bán vs POS thực tế */
    @Column(name = "discrepancy_pos", precision = 10, scale = 3)
    private BigDecimal discrepancyPos;

    /**
     * Nhân viên nhập — số bánh đã hủy cuối ngày.
     * Chỉ áp dụng cho sản phẩm hết HSD trong ngày (production_date + shelf_days ≤ reportDate).
     */
    @Column(name = "qty_cancelled", precision = 10, scale = 3)
    private BigDecimal qtyCancelled;

    /**
     * Chênh lệch hủy: qty_cancelled (NV nhập) - qty_system_cancel (hệ thống tính).
     * Âm = NV hủy ít hơn hệ thống dự kiến, dương = NV hủy nhiều hơn.
     * Chỉ có giá trị sau FINALIZE với sản phẩm hết HSD.
     */
    @Column(name = "discrepancy_cancel", precision = 10, scale = 3)
    private BigDecimal discrepancyCancel;

    /** Tồn đầu ngày = qty_remaining_actual của ngày hôm trước cùng item */
    @Column(name = "qty_remaining_opening", precision = 10, scale = 3)
    private BigDecimal qtyRemainingOpening;

    /**
     * Bánh Hủy Hệ Thống = tổng SHOP stock_lot còn lại của các lô hết HSD trong ngày.
     * Tính tại thời điểm FINALIZE.
     */
    @Column(name = "qty_system_cancel", precision = 10, scale = 3)
    private BigDecimal qtySystemCancel;

    /**
     * Còn lại Hệ Thống = qty_remaining_opening + qty_received - qty_sold_pos - qty_system_cancel.
     * Dùng để so sánh với qty_remaining_actual (NV nhập).
     */
    @Column(name = "qty_system_remaining", precision = 10, scale = 3)
    private BigDecimal qtySystemRemaining;

    /**
     * Chênh lệch còn lại = qty_remaining_actual - qty_system_remaining.
     * Âm = NV còn ít hơn HT dự kiến (mất hàng), dương = NV còn nhiều hơn (thừa).
     */
    @Column(name = "discrepancy_remaining", precision = 10, scale = 3)
    private BigDecimal discrepancyRemaining;

    /** Snapshot giá vốn tại thời điểm chốt */
    @Column(name = "unit_cost", precision = 15, scale = 2)
    private BigDecimal unitCost;

    /** Snapshot giá bán tại thời điểm chốt */
    @Column(name = "selling_price", precision = 15, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
}
