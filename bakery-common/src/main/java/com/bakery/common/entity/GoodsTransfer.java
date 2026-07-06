package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phiếu chuyển kho nguyên liệu.
 *
 * Code: TRF-{yyyyMMdd}-{seq}
 *
 * transfer_reason:
 *   PRODUCTION    — KHO_TONG → KHO_BEP (từ plan)
 *   RESTOCK       — KHO_TONG → KHO_BEP (bổ sung thêm)
 *   RETURN        — KHO_BEP  → KHO_TONG (bếp trả NL thừa)
 *   ADJUSTMENT    — trừ 1 kho (hàng mất/hư), to_branch_id = NULL, Chính duyệt
 *   WASTE_DISPOSAL— xuất NL hỏng để hủy
 *
 * Status flow:
 *   PENDING   → Cường thấy ở KHO_TONG, chuẩn bị hàng
 *   READY     → BEP thấy ở KHO_BEP, đến lấy và kiểm tra
 *   COMPLETED → BEP approve: atomic -from_branch +to_branch (FEFO)
 *   REJECTED  → BEP reject có lý do → hiện ở KHO_TONG rejected
 *   CANCELLED → hủy trước khi READY
 *
 *   ADJUSTMENT riêng:
 *   PENDING → Chính duyệt → COMPLETED (-from_branch, FEFO, không có +)
 */
@Entity
@Table(name = "goods_transfer")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GoodsTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_branch_id", nullable = false)
    private Branch fromBranch;

    /** NULL khi transfer_reason = ADJUSTMENT (chỉ trừ 1 kho, không có kho nhận) */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "to_branch_id", nullable = true)
    private Branch toBranch;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    @Column(name = "transfer_reason", nullable = false, length = 30)
    @Builder.Default
    private String transferReason = "PRODUCTION";

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /** Cường mark READY (đã chuẩn bị xong) */
    @Column(name = "ready_by", length = 100)
    private String readyBy;

    @Column(name = "ready_at")
    private OffsetDateTime readyAt;

    /** BEP approve (COMPLETED) — atomic inventory change */
    @Column(name = "confirmed_by", length = 100)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    /** BEP reject (lý do bắt buộc) */
    @Column(name = "rejected_by", length = 100)
    private String rejectedBy;

    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    /** Traceability: lô KHO_BEP ← phiếu TRF này ← lô KHO_TONG */
    @Column(name = "source_transfer_id")
    private UUID sourceTransferId;

    /** Phiếu gốc bị REJECT — dùng khi clone để tạo phiếu mới */
    @Column(name = "cloned_from_id")
    private UUID clonedFromId;

    /**
     * Nguồn tạo phiếu:
     *   AUTO_PLAN — sinh tự động từ production plan (autoGenerateFromPlan)
     *   MANUAL    — nhân viên tạo thủ công
     */
    @Column(name = "transfer_source", nullable = false, length = 20)
    @Builder.Default
    private String transferSource = "MANUAL";

    @Column(name = "created_by", nullable = false, length = 100)
    @Builder.Default
    private String createdBy = "system";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GoodsTransferLine> lines = new ArrayList<>();
}
