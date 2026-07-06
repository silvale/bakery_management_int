package com.bakery.common.entity;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode
public class ProductionGroupMemberId implements Serializable {
    private UUID groupId;
    private UUID productId;
}
