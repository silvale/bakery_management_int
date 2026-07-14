package com.bakery.api.production.entity;

import java.util.ArrayList;
import java.util.List;

import com.bakery.framework.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Nhóm sản xuất — Pattern 2 (FREE_GROUP) và Pattern 3 (BATCH_FORMULA).
 *
 * <p><b>FREE_GROUP</b>: Pana Cotta — tổng target cố định mỗi ngày, nhân viên tự phân bổ từng sốt.
 * <p><b>BATCH_FORMULA</b>: Bánh Bắp — 1 cối = N gram, mỗi size có gram/cái cố định.
 */
@Getter
@Setter
@Entity
@Table(name = "production_group")
public class ProductionGroup extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** FREE_GROUP | BATCH_FORMULA */
    @Column(name = "group_type", nullable = false, length = 20)
    private String groupType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_group_id")
    private ItemGroup itemGroup;

    // ── FREE_GROUP fields ─────────────────────────────────────────────────────

    /** Tổng số cần làm ngày thường (WEEKDAY). */
    @Column(name = "target_weekday")
    private Integer targetWeekday;

    /** Tổng số cần làm cuối tuần (WEEKEND). */
    @Column(name = "target_weekend")
    private Integer targetWeekend;

    /**
     * Ngưỡng kích hoạt sản xuất (%) — chỉ dùng cho FREE_GROUP.
     * Nếu set (ví dụ 50), nhóm chỉ sản xuất khi tổng tồn &lt; thresholdPercent% × target.
     * NULL = luôn sản xuất đủ target (hành vi mặc định).
     */
    @Column(name = "threshold_percent")
    private Integer thresholdPercent;

    // ── BATCH_FORMULA fields ──────────────────────────────────────────────────

    /** Trọng lượng 1 cối (gram). Ví dụ: 10000 = 10kg. */
    @Column(name = "batch_weight_grams")
    private Integer batchWeightGrams;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<ProductionGroupItem> items = new ArrayList<>();
}
