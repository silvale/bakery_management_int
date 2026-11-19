package com.bakery.api.auth.entity;

import com.bakery.framework.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import java.util.LinkedHashSet;
import java.util.Set;
import com.bakery.api.master.entity.Supplier;
import com.bakery.api.master.entity.Warehouse;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "user_account")
public class UserAccount extends BaseEntity {

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private UserRole role;

    /** 11/D2: kho được gán — chỉ thấy và thao tác dữ liệu của các kho này (Admin: tất cả). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_warehouse", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "warehouse_id"))
    private Set<Warehouse> warehouses = new LinkedHashSet<>();

    /** 11/D3: NCC mà user được xem và nhập giá. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_price_supplier", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "supplier_id"))
    private Set<Supplier> priceSuppliers = new LinkedHashSet<>();
}
