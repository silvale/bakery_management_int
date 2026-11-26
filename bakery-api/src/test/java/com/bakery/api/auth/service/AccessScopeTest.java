package com.bakery.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.auth.entity.RolePermission;
import com.bakery.api.auth.entity.UserAccount;
import com.bakery.api.auth.entity.UserRole;
import com.bakery.api.auth.repository.RolePermissionRepository;
import com.bakery.api.auth.repository.UserAccountRepository;
import com.bakery.api.auth.repository.UserRoleRepository;
import com.bakery.api.master.entity.Supplier;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.entity.WarehouseRole;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.security.BakeryUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/** 11: phạm vi kho, xem giá theo NCC, quyền theo vai trò. Repository dựng bằng Proxy (Mockito không chạy trên JDK 25). */
class AccessScopeTest {

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, java.util.function.BiFunction<String, Object[], Object> h) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (p, m, a) -> h.apply(m.getName(), a));
    }

    private static Warehouse wh(String code) {
        Warehouse w = new Warehouse();
        w.setId(UUID.randomUUID());
        w.setCode(code);
        w.setName("Kho " + code);
        return w;
    }

    private final Warehouse main = wh("MAIN"), kitchen = wh("KITCHEN");
    private final Supplier cho = supplier(), hd = supplier();
    private final UserRole role = new UserRole();
    private final UserAccount user = new UserAccount();
    private final java.util.List<String> extraActions = new java.util.ArrayList<>();   // quyền INVENTORY_REQUESTS thêm cho từng test

    private static Supplier supplier() {
        Supplier s = new Supplier();
        s.setId(UUID.randomUUID());
        return s;
    }

    private AccessScope scopeFor(String roleCode) {
        role.setId(UUID.randomUUID());
        role.setCode(roleCode);
        user.setId(UUID.randomUUID());
        user.setRole(role);
        user.getWarehouses().add(kitchen);
        user.getPriceSuppliers().add(cho);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new BakeryUserPrincipal(user.getId().toString(), "u", roleCode), null, List.of()));
        return new AccessScope(new BakeryActorResolver(),
                stub(UserAccountRepository.class, (n, a) -> "findWithScopeById".equals(n) ? Optional.of(user) : null),
                stub(WarehouseRepository.class, (n, a) -> switch (n) {
                    case "findAll", "findAllByStatus" -> List.of(main, kitchen);
                    case "findByCode" -> Optional.of("MAIN".equals(a[0]) ? main : kitchen);
                    default -> null;
                }),
                stub(UserRoleRepository.class, (n, a) -> Optional.of(role)),
                stub(RolePermissionRepository.class, (n, a) -> {
                    List<RolePermission> l = new java.util.ArrayList<>(List.of(
                            new RolePermission(role.getId(), "INVENTORY_REQUESTS", "UPDATE"),
                            new RolePermission(role.getId(), "INVENTORY_REQUESTS", "VIEW")));
                    extraActions.forEach(x -> l.add(new RolePermission(role.getId(), "INVENTORY_REQUESTS", x)));
                    return l;
                }));
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void staffOnlySeesAssignedWarehouses() {
        AccessScope s = scopeFor("KITCHEN_STAFF");
        assertThat(s.isAdmin()).isFalse();
        assertThat(s.warehouseCodes()).containsExactly("KITCHEN");
        s.assertWarehouse(kitchen);
        assertThatThrownBy(() -> s.assertWarehouse(main)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403").hasMessageContaining("Kho MAIN");
        s.assertAnyWarehouse(main, kitchen);          // kho xuất MAIN + kho nhận KITCHEN → hợp lệ
        s.assertAnyWarehouse(null, kitchen);
        assertThatThrownBy(() -> s.assertAnyWarehouse(main, null)).isInstanceOf(ResponseStatusException.class);
        // 22: @RequireWarehouse theo việc của kho, không theo mã
        kitchen.setDeliveryHold(true);
        main.setCanReceivePurchase(true);
        assertThatThrownBy(() -> s.assertAnyWarehouseRole(WarehouseRole.SALES_POINT, WarehouseRole.RECEIVE_PURCHASE))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        s.assertAnyWarehouseRole(WarehouseRole.SALES_POINT, WarehouseRole.DELIVERY_HOLD);
    }

    @Test
    void stockOfPickSourceNeedsApproveAndAssignment() {   // 24/§5.8
        extraActions.add("APPROVE");
        AccessScope manager = scopeFor("KITCHEN_MANAGER");     // có APPROVE, được gán KITCHEN
        assertThat(manager.canSeeStockOf(kitchen)).isTrue();
        assertThat(manager.canSeeStockOf(main)).isFalse();     // không được gán MAIN
        assertThat(manager.canSeeStockOf(null)).isFalse();
        extraActions.clear();
        AccessScope staff = scopeFor("KITCHEN_STAFF");         // được gán KITCHEN nhưng không có APPROVE
        assertThat(staff.canSeeStockOf(kitchen)).isFalse();
        SecurityContextHolder.clearContext();
        user.setRole(role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new BakeryUserPrincipal(user.getId().toString(), "a", "SUPER_ADMIN"), null, List.of()));
        assertThat(manager.canSeeStockOf(main)).isTrue();      // Admin thấy mọi kho
    }

    @Test
    void priceVisibilityFollowsAssignedSuppliers() {
        AccessScope s = scopeFor("KITCHEN_STAFF");
        assertThat(s.canSeePurchasePrice(cho.getId())).isTrue();
        assertThat(s.canSeePurchasePrice(hd.getId())).isFalse();
        assertThat(s.canSeePurchasePrice(null)).isFalse();     // phiếu/lô không NCC: chỉ Admin
        assertThat(s.canSeeCost()).isFalse();
    }

    @Test
    void permissionsComeFromRole() {
        AccessScope s = scopeFor("KITCHEN_STAFF");
        assertThat(s.can("INVENTORY_REQUESTS", "UPDATE")).isTrue();
        assertThat(s.can("INVENTORY_REQUESTS", "UPDATE_ANY")).isFalse();
        assertThat(s.can("COST_PRICE", "VIEW")).isFalse();
        assertThatThrownBy(() -> s.require("INVENTORY_REQUESTS", "RECEIVE")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void recipeVisibilityFollowsPermissionNotRoleName() {   // 17/D1: vai trò không có RECIPES:VIEW → không thấy công thức/BTP
        assertThat(scopeFor("KITCHEN_STAFF").canViewRecipes()).isFalse();
        SecurityContextHolder.clearContext();
        AccessScope withPerm = new AccessScope(new BakeryActorResolver(),
                null, null,
                stub(UserRoleRepository.class, (n, a) -> Optional.of(role)),
                stub(RolePermissionRepository.class, (n, a) -> List.of(new RolePermission(role.getId(), "RECIPES", "VIEW"))));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new BakeryUserPrincipal(user.getId().toString(), "u", "ANY_ROLE_NAME"), null, List.of()));
        assertThat(withPerm.canViewRecipes()).isTrue();
    }

    @Test
    void itemTabPermissionIsIndependentOfRecipes() {   // 19/D3: ITEMS_SEMI tách khỏi RECIPES
        scopeFor("KITCHEN_STAFF");   // khởi tạo id user/role như test 17
        SecurityContextHolder.clearContext();
        AccessScope semiOnly = new AccessScope(new BakeryActorResolver(), null, null,
                stub(UserRoleRepository.class, (n, a) -> Optional.of(role)),
                stub(RolePermissionRepository.class, (n, a) -> List.of(
                        new RolePermission(role.getId(), "ITEMS_SEMI", "VIEW"),
                        new RolePermission(role.getId(), "ITEMS_PRODUCT", "VIEW"))));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new BakeryUserPrincipal(user.getId().toString(), "u", "ANY_ROLE_NAME"), null, List.of()));
        assertThat(semiOnly.canViewItemType("SEMI_PRODUCT")).isTrue();
        assertThat(semiOnly.canViewItemType("product")).isTrue();            // không phân biệt hoa thường
        assertThat(semiOnly.canViewItemType("INGREDIENT")).isFalse();
        assertThat(semiOnly.canViewRecipes()).isFalse();                      // thấy tab BTP nhưng không thấy công thức
        assertThatThrownBy(() -> semiOnly.canViewItemType("CAKE")).isInstanceOf(IllegalArgumentException.class);
        assertThat(scopeFor(AccessScope.SUPER_ADMIN).canViewItemType("INGREDIENT")).isTrue();   // Admin bypass
    }

    @Test
    void adminSeesEverything() {
        AccessScope s = scopeFor(AccessScope.SUPER_ADMIN);
        assertThat(s.isAdmin()).isTrue();
        assertThat(s.canSeePurchasePrice(null)).isTrue();
        assertThat(s.canSeeCost()).isTrue();
        assertThat(s.canViewRecipes()).isTrue();     // 17: Admin luôn xem được công thức
        assertThat(s.can("ANY", "THING")).isTrue();
        assertThat(s.warehouseIds()).hasSize(2);
        s.assertWarehouse(main);
    }

    @Test
    void internalCallWithoutUserIsUnrestricted() {   // job nền / luồng nội bộ ngoài request HTTP
        SecurityContextHolder.clearContext();
        AccessScope s = new AccessScope(new BakeryActorResolver(), null, null, null, null);
        assertThat(s.isAdmin()).isTrue();
        assertThat(s.canSeeCost()).isTrue();
    }

    // 18: bốn nhánh khóa vai trò — sửa quyền, đổi mã, SUPER_ADMIN, xóa
    private static UserRole role(String code, boolean system) {
        UserRole r = new UserRole();
        r.setCode(code);
        r.setName("Tên cũ");
        r.setSystem(system);
        return r;
    }

    private final UserRoleService roles = new UserRoleService(null, null, null);

    @Test
    void defaultRolesCanChangePermissionsButAdminCannot() {
        UserRoleService.requireCanEditPermissions(role("SHOP_STAFF", true));
        UserRoleService.requireCanEditPermissions(role("TU_TAO", false));
        assertThatThrownBy(() -> UserRoleService.requireCanEditPermissions(role("SUPER_ADMIN", true)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Không sửa quyền của vai trò Admin");
        assertThat(com.bakery.api.auth.dto.UserRoleResponse.from(role("SHOP_STAFF", true)).isCanEditPermissions()).isTrue();
        assertThat(com.bakery.api.auth.dto.UserRoleResponse.from(role("SUPER_ADMIN", true)).isCanEditPermissions()).isFalse();
    }

    @Test
    void defaultRoleRenamesButKeepsCode() {
        UserRole r = role("SHOP_STAFF", true);
        roles.applyUpdate(r, new com.bakery.api.auth.dto.UserRoleRequest("SHOP_STAFF", "Nhân viên bán hàng", "mô tả mới"));
        assertThat(r.getName()).isEqualTo("Nhân viên bán hàng");
        assertThat(r.getDescription()).isEqualTo("mô tả mới");
        assertThat(r.getCode()).isEqualTo("SHOP_STAFF");
        assertThatThrownBy(() -> roles.applyUpdate(r, new com.bakery.api.auth.dto.UserRoleRequest("SHOP_X", "x", null)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Không đổi mã vai trò mặc định");
        assertThat(r.getCode()).isEqualTo("SHOP_STAFF");
        UserRole custom = role("TU_TAO", false);
        roles.applyUpdate(custom, new com.bakery.api.auth.dto.UserRoleRequest("MA_MOI", "x", null));   // vai trò tự tạo: như cũ
        assertThat(custom.getCode()).isEqualTo("MA_MOI");
    }

    @Test
    void adminRoleIsFullyLockedAndDefaultRolesCannotBeDeleted() {
        UserRole admin = role("SUPER_ADMIN", true);
        assertThatThrownBy(() -> roles.applyUpdate(admin, new com.bakery.api.auth.dto.UserRoleRequest("SUPER_ADMIN", "Tên mới", null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> roles.applyUpdate(admin, new com.bakery.api.auth.dto.UserRoleRequest("KHAC", "Admin", null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(admin.getName()).isEqualTo("Tên cũ");
        assertThatThrownBy(() -> roles.beforeDelete(admin)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> roles.beforeDelete(role("SHOP_STAFF", true))).isInstanceOf(IllegalStateException.class)
                .hasMessage("Vai trò mặc định không xóa được");
        roles.beforeDelete(role("TU_TAO", false));
    }
}
