package com.bakery.api.production.entity;

import com.bakery.framework.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Nhóm phòng/bộ phận sản xuất.
 * Ví dụ: PL=Phòng Lạnh, PK=Phòng Kem, BMN=Bánh Mì Ngọt.
 *
 * <p>Dùng để filter sản phẩm theo khu vực sản xuất (catalog dimension).
 * Khác với {@link ProductionGroup} — đó là nhóm kế hoạch SX (planning dimension).
 */
@Getter
@Setter
@Entity
@Table(name = "item_group")
public class ItemGroup extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
