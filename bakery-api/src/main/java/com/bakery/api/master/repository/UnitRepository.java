/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.repository;

import com.bakery.api.master.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnitRepository extends JpaRepository<Unit, String> {

    /** Số mặt hàng dùng đơn vị làm đơn vị chính hoặc đóng gói (index item.unit, item_packaging.code). */
    @org.springframework.data.jpa.repository.Query(nativeQuery = true, value = """
            SELECT count(*) FROM (
                SELECT id FROM item WHERE unit = :code
                UNION
                SELECT item_id FROM item_packaging WHERE code = :code) t""")
    long countItemsUsing(@org.springframework.data.repository.query.Param("code") String code);
}
