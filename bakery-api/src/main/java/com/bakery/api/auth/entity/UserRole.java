package com.bakery.api.auth.entity;

import com.bakery.framework.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "user_role")
public class UserRole extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** Vai trò mặc định (7 vai trò seed): không xóa, không đổi mã; Admin còn khóa tên và quyền (18/D1–D4). */
    @Column(name = "is_system", nullable = false)
    private boolean system = false;

    /** Kho gán mặc định khi chọn vai trò, vd "KITCHEN,COLD". */
    @Column(name = "default_warehouse_codes", length = 200)
    private String defaultWarehouseCodes;

    /** Vai trò có quyền xem/nhập giá theo NCC (D3). */
    @Column(name = "price_by_supplier", nullable = false)
    private boolean priceBySupplier = false;
}
