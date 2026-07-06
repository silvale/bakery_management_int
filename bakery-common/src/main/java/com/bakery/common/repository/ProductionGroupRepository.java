package com.bakery.common.repository;

import com.bakery.common.entity.ProductionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductionGroupRepository extends JpaRepository<ProductionGroup, UUID> {

    List<ProductionGroup> findAllByActiveTrue();

    Optional<ProductionGroup> findByGroupCode(String groupCode);

    /** Tìm group chứa productId — dùng trong GROUP_SUBTRACT engine */
    @Query("""
        SELECT g FROM ProductionGroup g
        JOIN g.members m
        WHERE m.product.id = :productId AND g.active = true
        """)
    Optional<ProductionGroup> findActiveGroupByProductId(UUID productId);

    /** Tất cả product_id trong 1 group — dùng để lấy tồn kho tổng nhóm */
    @Query("""
        SELECT m.product.id FROM ProductionGroupMember m
        WHERE m.group.id = :groupId
        """)
    List<UUID> findProductIdsByGroupId(UUID groupId);
}
