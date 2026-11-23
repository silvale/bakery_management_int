package com.bakery.api.auth.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bakery.api.auth.entity.UserAccount;
import com.bakery.api.auth.repository.RolePermissionRepository;
import com.bakery.api.auth.repository.UserAccountRepository;
import com.bakery.api.auth.repository.UserRoleRepository;
import com.bakery.api.master.entity.Supplier;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.security.BakeryUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ResponseStatusException;

/**
 * 11: phạm vi truy cập của user hiện tại — kho được gán (D2), NCC được xem giá (D3), giá vốn (D5).
 *
 * <p>Admin ({@code SUPER_ADMIN}) thấy tất cả. Gọi ngoài một request HTTP (job nền, luồng nội bộ không có user)
 * thì không giới hạn; trong một request HTTP mà chưa đăng nhập thì 401. Dữ liệu được cache theo request.
 */
@Component
@RequiredArgsConstructor
public class AccessScope {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ATTR_USER = AccessScope.class.getName() + ".user";
    private static final String ATTR_PERMS = AccessScope.class.getName() + ".perms";

    private final BakeryActorResolver actorResolver;
    private final UserAccountRepository userAccountRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    // ── Danh tính ────────────────────────────────────────────

    /** Có user đăng nhập hay không (true cả khi chạy ngoài HTTP là không). */
    private boolean internalCall(BakeryUserPrincipal p) {
        if (p != null) return false;
        if (RequestContextHolder.getRequestAttributes() != null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập.");
        }
        return true;   // luồng nội bộ (job, listener) không có user → không giới hạn
    }

    public boolean isAdmin() {
        BakeryUserPrincipal p = actorResolver.currentUser();
        return internalCall(p) || SUPER_ADMIN.equalsIgnoreCase(p.getRole());
    }

    public void requireAdmin() {
        if (!isAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ Admin được thực hiện thao tác này.");
    }

    public String currentUserId() { return actorResolver.currentUserId(); }

    /** Quyền theo vai trò (screen:action). Admin luôn true. Cache theo request. */
    public boolean can(String screen, String action) {
        if (isAdmin()) return true;
        String role = actorResolver.currentUser().getRole();
        Set<String> perms = cached(ATTR_PERMS + role, () -> userRoleRepository.findByCode(role)
                .map(r -> rolePermissionRepository.findByIdRoleId(r.getId()).stream()
                        .map(rp -> rp.getId().getScreenCode() + ":" + rp.getId().getActionCode())
                        .collect(Collectors.toSet()))
                .orElse(Set.of()));
        return perms.contains(screen + ":" + action);
    }

    public void require(String screen, String action) {
        if (!can(screen, action)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Không có quyền thực hiện thao tác này [" + screen + ":" + action + "]");
        }
    }

    // ── Kho (D2) ─────────────────────────────────────────────

    /** Id các kho user được thấy; Admin/luồng nội bộ → mọi kho. */
    public Set<UUID> warehouseIds() {
        if (isAdmin()) {
            return warehouseRepository.findAll().stream().map(Warehouse::getId).collect(Collectors.toSet());
        }
        return warehouses().stream().map(Warehouse::getId).collect(Collectors.toSet());
    }

    public Set<String> warehouseCodes() {
        if (isAdmin()) return warehouseRepository.findAll().stream().map(Warehouse::getCode).collect(Collectors.toSet());
        return warehouses().stream().map(Warehouse::getCode).collect(Collectors.toSet());
    }

    public List<Warehouse> warehouses() {
        if (isAdmin()) return warehouseRepository.findAll();
        return load().map(u -> List.copyOf(u.getWarehouses())).orElse(List.of());
    }

    public boolean hasWarehouse(UUID warehouseId) {
        return warehouseId != null && (isAdmin() || warehouseIds().contains(warehouseId));
    }

    public boolean hasWarehouseCode(String code) {
        return code != null && (isAdmin() || warehouseCodes().contains(code));
    }

    /** → 403 nếu kho không thuộc phạm vi. */
    public void assertWarehouse(Warehouse w) {
        if (w == null || !hasWarehouse(w.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bạn không có quyền với kho " + (w != null ? w.getName() : ""));
        }
    }

    public void assertWarehouseCode(String code) {
        if (!hasWarehouseCode(code)) {
            String name = warehouseRepository.findByCode(code).map(Warehouse::getName).orElse(code);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với kho " + name);
        }
    }

    /** Cần ít nhất một trong các kho (vd phiếu có kho xuất hoặc kho nhận thuộc phạm vi). Bỏ qua phần tử null. */
    public void assertAnyWarehouse(Warehouse... ws) {
        if (isAdmin()) return;
        for (Warehouse w : ws) if (w != null && hasWarehouse(w.getId())) return;
        String names = java.util.Arrays.stream(ws).filter(java.util.Objects::nonNull).map(Warehouse::getName)
                .collect(Collectors.joining(", "));
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với kho " + names);
    }

    /** Cần ít nhất một trong các mã kho (vd KITCHEN hoặc SHOP). */
    public void assertAnyWarehouseCode(String... codes) {
        if (isAdmin()) return;
        for (String c : codes) if (hasWarehouseCode(c)) return;
        String names = java.util.Arrays.stream(codes)
                .map(c -> warehouseRepository.findByCode(c).map(Warehouse::getName).orElse(c))
                .collect(Collectors.joining(" hoặc "));
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền với kho " + names);
    }

    // ── Giá (D3, D5, D6) ─────────────────────────────────────

    public Set<UUID> priceSupplierIds() {
        if (isAdmin()) return Collections.emptySet();   // dùng canSeePurchasePrice, không liệt kê
        return load().map(u -> u.getPriceSuppliers().stream().map(Supplier::getId).collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    /** Xem/nhập giá nhập của NCC này? Admin: luôn. {@code supplierId} null (không NCC) → chỉ Admin. */
    public boolean canSeePurchasePrice(UUID supplierId) {
        if (isAdmin()) return true;
        return supplierId != null && priceSupplierIds().contains(supplierId);
    }

    /** Giá vốn, chi phí, giá trị điều chỉnh, doanh thu... — chỉ Admin (D5). */
    public boolean canSeeCost() { return isAdmin(); }

    /** Xem công thức và danh mục bán thành phẩm (17/D1): quyền RECIPES:VIEW, Admin luôn có. Theo quyền, không theo tên vai trò. */
    public boolean canViewRecipes() { return can("RECIPES", "VIEW"); }

    // ── nội bộ ───────────────────────────────────────────────

    private Optional<UserAccount> load() {
        BakeryUserPrincipal p = actorResolver.currentUser();
        if (p == null) return Optional.empty();
        return cached(ATTR_USER, () -> {
            try {
                return userAccountRepository.findWithScopeById(UUID.fromString(p.getUserId()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String attr, java.util.function.Supplier<T> loader) {
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (ra == null) return loader.get();
        Object v = ra.getAttribute(attr, RequestAttributes.SCOPE_REQUEST);
        if (v == null) {
            v = loader.get();
            ra.setAttribute(attr, v, RequestAttributes.SCOPE_REQUEST);
        }
        return (T) v;
    }
}
