package com.bakery.api.master.entity;

import com.bakery.framework.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "warehouse")
public class Warehouse extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "address", length = 500)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private WarehouseKind kind = WarehouseKind.INTERNAL;

    @Column(name = "can_receive_purchase", nullable = false)
    private boolean canReceivePurchase;

    @Column(name = "is_production_source", nullable = false)
    private boolean productionSource;

    @Column(name = "can_store_semi", nullable = false)
    private boolean canStoreSemi;

    @Column(name = "is_delivery_hold", nullable = false)
    private boolean deliveryHold;

    @Column(name = "is_sales_point", nullable = false)
    private boolean salesPoint;

    @Column(name = "can_export_external", nullable = false)
    private boolean canExportExternal;

    /** 24: kho bảng tạm "Đang sản xuất" của cửa hàng; không bật cờ nào khác. */
    @Column(name = "is_work_in_progress", nullable = false)
    private boolean workInProgress;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** 23: cửa hàng chứa kho này; null = không thuộc cửa hàng nào (EXTERNAL luôn null). Bảng nhỏ nên EAGER. */
    @ManyToOne
    @JoinColumn(name = "store_id")
    private Store store;

    public boolean has(WarehouseRole role) {
        return switch (role) {
            case RECEIVE_PURCHASE -> canReceivePurchase;
            case PRODUCTION_SOURCE -> productionSource;
            case STORE_SEMI -> canStoreSemi;
            case DELIVERY_HOLD -> deliveryHold;
            case SALES_POINT -> salesPoint;
            case EXPORT_EXTERNAL -> canExportExternal;
            case WORK_IN_PROGRESS -> workInProgress;
        };
    }

    public boolean isExternal() { return kind == WarehouseKind.EXTERNAL; }
}
