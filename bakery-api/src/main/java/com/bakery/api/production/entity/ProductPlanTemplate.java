package com.bakery.api.production.entity;

import com.bakery.framework.entity.BaseEntity;
import com.bakery.framework.entity.DayType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Khung kế hoạch điều phối sản xuất động.
 * Dùng để sinh dữ liệu mẫu cho ProductionRequest.
 * Anh Chính sẽ chỉnh trực tiếp trên ProductionRequestLine — template chỉ là điểm khởi đầu.
 */
@Getter
@Setter
@Entity
@Table(name = "product_plan_template")
public class ProductPlanTemplate extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 10)
    private DayType dayType;

    @Column(name = "is_active", nullable = false)
    private boolean active = false;

    @Column(name = "description", length = 500)
    private String description;
}
