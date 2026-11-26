package com.bakery.api.auth.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.bakery.api.auth.entity.ScreenRegistry;
import com.bakery.api.auth.repository.ScreenRegistryRepository;
import org.springframework.stereotype.Component;

/**
 * 26: câu báo thiếu quyền bằng tiếng Việt: tên màn lấy từ {@code screen_registry}, tên thao tác từ bảng dưới.
 * Dùng tĩnh để chỗ nào ném 403 cũng gọi được; chưa có bean (test đơn vị) hoặc màn lạ thì bỏ phần tên.
 */
@Component
public class PermissionLabels {

    private static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry("VIEW", "Xem"), Map.entry("CREATE", "Tạo"), Map.entry("UPDATE", "Sửa"),
            Map.entry("UPDATE_ANY", "Sửa mọi phiếu"), Map.entry("DELETE", "Xóa"), Map.entry("APPROVE", "Duyệt"),
            Map.entry("REJECT", "Từ chối"), Map.entry("HISTORY", "Xem lịch sử"), Map.entry("RECEIVE", "Nhận hàng"),
            Map.entry("FINALIZE", "Chốt"), Map.entry("COMPLETE", "Hoàn thành"), Map.entry("EXTRA_MATERIAL", "Lấy thêm NL"));

    private static volatile ScreenRegistryRepository repo;
    private static final Map<String, String> SCREENS = new ConcurrentHashMap<>();

    public PermissionLabels(ScreenRegistryRepository r) { repo = r; }

    /** Tên thao tác tiếng Việt; thao tác lạ giữ nguyên mã. */
    public static String action(String code) { return ACTIONS.getOrDefault(code, code); }

    /** Tên màn từ {@code screen_registry}; không tra được → null. */
    public static String screen(String code) {
        if (code == null || repo == null) return null;
        String n = SCREENS.get(code);
        if (n == null) {
            n = repo.findById(code).map(ScreenRegistry::getName).orElse(null);
            if (n != null) SCREENS.put(code, n);
        }
        return n;
    }

    /** "Không có quyền thực hiện thao tác này (Phiếu kho – Tạo)". */
    public static String denied(String screen, String action) {
        String s = screen(screen);
        return "Không có quyền thực hiện thao tác này" + (s == null ? "" : " (" + s + " – " + action(action) + ")");
    }
}
