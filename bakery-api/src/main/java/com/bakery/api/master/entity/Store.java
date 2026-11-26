package com.bakery.api.master.entity;

import com.bakery.framework.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 23: cửa hàng = nhóm để gom kho khi hiển thị. Không mang nghiệp vụ bán hàng / sản xuất. */
@Getter
@Setter
@Entity
@Table(name = "store")
public class Store extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
