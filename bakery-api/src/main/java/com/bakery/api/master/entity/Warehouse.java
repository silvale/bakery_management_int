package com.bakery.api.master.entity;

import com.bakery.framework.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public boolean has(WarehouseRole role) {
        return switch (role) {
            case RECEIVE_PURCHASE -> canReceivePurchase;
            case PRODUCTION_SOURCE -> productionSource;
            case STORE_SEMI -> canStoreSemi;
            case DELIVERY_HOLD -> deliveryHold;
            case SALES_POINT -> salesPoint;
            case EXPORT_EXTERNAL -> canExportExternal;
        };
    }

    public boolean isExternal() { return kind == WarehouseKind.EXTERNAL; }
}
