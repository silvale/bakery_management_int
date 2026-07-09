package com.bakery.api.master.entity;

import com.bakery.framework.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "code_value")
public class CodeValue extends BaseEntity {

    @Column(name = "group_key", nullable = false, length = 50)
    private String groupKey;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
