package com.bakery.api.master.service;

import java.util.Comparator;
import java.util.List;

import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.entity.WarehouseRole;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.framework.entity.EntityStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 22: nơi duy nhất trả lời "kho nào làm việc X". Mọi luồng hỏi ở đây; không đoạn code nào được so mã hay loại kho.
 * Đọc DB mỗi lần gọi (không cache) để Admin đổi cấu hình có hiệu lực ngay.
 */
@Component
@RequiredArgsConstructor
public class WarehouseRoles {

    static final Comparator<Warehouse> ORDER =
            Comparator.comparingInt(Warehouse::getSortOrder).thenComparing(Warehouse::getCode);

    private final WarehouseRepository repository;

    /** Kho bán hàng (đúng một kho). */
    public Warehouse salesPoint() { return single(WarehouseRole.SALES_POINT, "bán hàng"); }

    /** Kho bánh chờ giao (đúng một kho). */
    public Warehouse deliveryHold() { return single(WarehouseRole.DELIVERY_HOLD, "bánh chờ giao"); }

    /** Kho đang dùng có việc {@code r}, xếp theo thứ tự rồi mã. */
    public List<Warehouse> withRole(WarehouseRole r) {
        return filter(active(), r);
    }

    public boolean has(Warehouse w, WarehouseRole r) {
        return w != null && w.has(r);
    }

    /** Nhóm 1 (§5.2): lấy NL cho sản xuất và không nhận NCC (Phòng Bánh Mì, Phòng Lạnh). */
    public List<Warehouse> productionPrimary() { return primary(active()); }

    /** Nhóm 2 (§5.2): lấy NL cho sản xuất và nhận NCC (Kho Chính); chỉ dùng khi nhóm 1 thiếu. */
    public List<Warehouse> productionFallback() { return fallback(active()); }

    // ── Hàm thuần (không cần Spring/DB) cho unit test ───────────────────────

    static List<Warehouse> filter(List<Warehouse> all, WarehouseRole r) {
        return all.stream().filter(w -> !w.isExternal() && w.has(r)).sorted(ORDER).toList();
    }

    static List<Warehouse> primary(List<Warehouse> all) {
        return filter(all, WarehouseRole.PRODUCTION_SOURCE).stream().filter(w -> !w.isCanReceivePurchase()).toList();
    }

    static List<Warehouse> fallback(List<Warehouse> all) {
        return filter(all, WarehouseRole.PRODUCTION_SOURCE).stream().filter(Warehouse::isCanReceivePurchase).toList();
    }

    private List<Warehouse> active() {
        return repository.findAllByStatus(EntityStatus.ACTIVE);
    }

    private Warehouse single(WarehouseRole r, String label) {
        return withRole(r).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Chưa cấu hình kho " + label));
    }
}
