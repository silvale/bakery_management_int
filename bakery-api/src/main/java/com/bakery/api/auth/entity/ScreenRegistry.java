package com.bakery.api.auth.entity;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "screen_registry")
public class ScreenRegistry {

    @Id
    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Comma-separated list: VIEW,CREATE,UPDATE,DELETE,APPROVE,REJECT,HISTORY,FINALIZE */
    @Column(name = "available_actions", nullable = false)
    private String availableActions;

    @Column(name = "sort_order")
    private Integer sortOrder;

    public List<String> getAvailableActionList() {
        return Arrays.asList(availableActions.split(","));
    }
}
