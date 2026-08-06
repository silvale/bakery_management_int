package com.bakery.api.report.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.bakery.api.master.entity.Item;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Ghi nhận hủy bánh theo từng EX_CODE trong ngày báo cáo.
 *
 * <p>cancel_type:
 * <ul>
 *   <li>EXPIRED  — hết HSD, do hệ thống tạo khi init report (qty_cancel_expected > 0)</li>
 *   <li>DAMAGED  — hỏng/mốc, do NV thêm thủ công (qty_cancel_expected = 0)</li>
 *   <li>OTHER    — lý do khác, do NV thêm thủ công</li>
 * </ul>
 *
 * <p>Còn lại = qty_opening + qty_received - qty_cancel_actual - qty_sold_pos (tính trong service).
 */
@Getter
@Setter
@Entity
@Table(name = "cancel_record")
public class CancelRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private DailyReport dailyReport;

    /** EX_CODE của lô bánh cần hủy */
    @Column(name = "ex_code", nullable = false, length = 50)
    private String exCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;

    /** Ngày sản xuất của lô này */
    @Column(name = "production_date")
    private LocalDate productionDate;

    /** EXPIRED | DAMAGED | OTHER */
    @Column(name = "cancel_type", nullable = false, length = 20)
    private String cancelType = "EXPIRED";

    /** Tồn đầu ngày của EX_CODE này (snapshot lúc init) */
    @Column(name = "qty_opening", nullable = false, precision = 10, scale = 3)
    private BigDecimal qtyOpening = BigDecimal.ZERO;

    /** Số lượng thực nhận hôm nay (từ delivery_record) */
    @Column(name = "qty_received", nullable = false, precision = 10, scale = 3)
    private BigDecimal qtyReceived = BigDecimal.ZERO;

    /** Hủy dự kiến do hệ thống tính (0 với DAMAGED/OTHER) */
    @Column(name = "qty_cancel_expected", nullable = false, precision = 10, scale = 3)
    private BigDecimal qtyCancelExpected = BigDecimal.ZERO;

    /** Hủy thực tế do NV nhập (null = chưa xác nhận) */
    @Column(name = "qty_cancel_actual", precision = 10, scale = 3)
    private BigDecimal qtyCancelActual;

    /** NV đã xác nhận hủy chưa */
    @Column(name = "confirmed", nullable = false)
    private boolean confirmed = false;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
