package com.bakery.api.auth.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "role_permission")
@NoArgsConstructor
public class RolePermission {

    @EmbeddedId
    private RolePermissionId id;

    public RolePermission(UUID roleId, String screenCode, String actionCode) {
        this.id = new RolePermissionId(roleId, screenCode, actionCode);
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class RolePermissionId implements Serializable {

        @Column(name = "role_id")
        private UUID roleId;

        @Column(name = "screen_code", length = 50)
        private String screenCode;

        @Column(name = "action_code", length = 30)
        private String actionCode;
    }
}
