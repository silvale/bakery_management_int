package com.bakery.common.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "role_permission")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermission {

    @EmbeddedId
    private RolePermissionId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id")
    private UserRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("screenId")
    @JoinColumn(name = "screen_id")
    private ScreenRegistry screen;

    @Column(name = "can_view", nullable = false)
    @Builder.Default
    private Boolean canView = false;

    @Column(name = "can_create", nullable = false)
    @Builder.Default
    private Boolean canCreate = false;

    @Column(name = "can_edit", nullable = false)
    @Builder.Default
    private Boolean canEdit = false;

    @Column(name = "can_delete", nullable = false)
    @Builder.Default
    private Boolean canDelete = false;

    @Column(name = "can_approve", nullable = false)
    @Builder.Default
    private Boolean canApprove = false;
}
