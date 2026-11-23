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
                    case "findAll" -> List.of(main, kitchen);
                    case "findByCode" -> Optional.of("MAIN".equals(a[0]) ? main : kitchen);
                    default -> null;
                }),
                stub(UserRoleRepository.class, (n, a) -> Optional.of(role)),
                stub(RolePermissionRepository.class, (n, a) -> List.of(
                        new RolePermission(role.getId(), "INVENTORY_REQUESTS", "UPDATE"),
                        new RolePermission(role.getId(), "INVENTORY_REQUESTS", "VIEW"))));
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
        assertThatThrownBy(() -> s.assertAnyWarehouseCode("SHOP", "MAIN")).isInstanceOf(ResponseStatusException.class);
        s.assertAnyWarehouseCode("SHOP", "KITCHEN");
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

    @Test
    void systemRolesAreLocked() {
        UserRole r = new UserRole();
        r.setSystem(true);
        assertThatThrownBy(() -> UserRoleService.requireNotSystem(r)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Vai trò hệ thống không sửa được");
        r.setSystem(false);
        UserRoleService.requireNotSystem(r);
    }
}
