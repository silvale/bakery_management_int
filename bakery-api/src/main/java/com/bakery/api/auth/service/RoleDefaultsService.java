package com.bakery.api.auth.service;

import java.util.ArrayList;
import java.util.List;

import com.bakery.api.auth.dto.RefInfo;
import com.bakery.api.auth.dto.RoleDefaults;
import com.bakery.api.auth.entity.UserRole;
import com.bakery.api.master.entity.Supplier;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.repository.SupplierRepository;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.framework.entity.EntityStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Kho và NCC xem giá điền sẵn theo vai trò (11/D2, D3). */
@Service
@RequiredArgsConstructor
public class RoleDefaultsService {

    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;

    @Transactional(readOnly = true)
    public List<Warehouse> defaultWarehouses(UserRole role) {
        List<Warehouse> out = new ArrayList<>();
        if (role == null || role.getDefaultWarehouseCodes() == null) return out;
        for (String code : role.getDefaultWarehouseCodes().split(",")) {
            if (!code.isBlank()) warehouseRepository.findByCode(code.trim()).ifPresent(out::add);
        }
        return out;
    }

    /** Vai trò có quyền giá theo NCC → mọi NCC "mua lẻ / trả ngay" đang dùng. */
    @Transactional(readOnly = true)
    public List<Supplier> defaultPriceSuppliers(UserRole role) {
        if (role == null || !role.isPriceBySupplier()) return List.of();
        return supplierRepository.findByCashPurchaseTrueAndStatus(EntityStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public RoleDefaults defaults(UserRole role) {
        return new RoleDefaults(
                defaultWarehouses(role).stream().map(w -> new RefInfo(w.getId(), w.getCode(), w.getName(),
                        w.getKind() != null ? w.getKind().name() : null)).toList(),
                defaultPriceSuppliers(role).stream().map(s -> RefInfo.of(s.getId(), s.getCode(), s.getName())).toList());
    }
}
