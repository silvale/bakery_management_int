package com.bakery.framework.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu endpoint cần kiểm tra quyền truy cập.
 *
 * <p>SUPER_ADMIN luôn bypass. Nếu không có role hợp lệ trong SecurityContext → 403.
 *
 * <pre>{@code
 * @RequirePermission(screen = "ITEMS", action = "CREATE")
 * @PostMapping
 * public ResponseEntity<ItemResponse> create(...) { ... }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    /** Mã màn hình — phải khớp với screen_registry.code */
    String screen();

    /** Mã action: VIEW | CREATE | UPDATE | DELETE | APPROVE | REJECT | HISTORY | FINALIZE */
    String action();
}
