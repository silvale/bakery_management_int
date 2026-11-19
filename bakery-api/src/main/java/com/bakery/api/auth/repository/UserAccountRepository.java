package com.bakery.api.auth.repository;

import java.util.Optional;

import com.bakery.api.auth.entity.UserAccount;
import com.bakery.framework.repository.BaseRepository;

public interface UserAccountRepository extends BaseRepository<UserAccount> {
    Optional<UserAccount> findByUsername(String username);
    boolean existsByUsername(String username);

    /** User kèm kho và NCC xem giá (fetch sẵn để dùng ngoài transaction, 11). */
    @org.springframework.data.jpa.repository.Query("""
            SELECT DISTINCT u FROM UserAccount u LEFT JOIN FETCH u.warehouses LEFT JOIN FETCH u.priceSuppliers
            LEFT JOIN FETCH u.role WHERE u.id = :id""")
    Optional<UserAccount> findWithScopeById(@org.springframework.data.repository.query.Param("id") java.util.UUID id);
}
