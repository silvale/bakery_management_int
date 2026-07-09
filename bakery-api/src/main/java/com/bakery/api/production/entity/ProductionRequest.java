package com.bakery.api.production.entity;

import java.time.LocalDate;

import com.bakery.api.master.entity.Warehouse;
import com.bakery.framework.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "production_request")
public class ProductionRequest extends BaseEntity {

    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(name = "note", length = 500)
    private String note;
}
