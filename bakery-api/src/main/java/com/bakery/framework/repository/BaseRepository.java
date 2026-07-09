package com.bakery.framework.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import com.bakery.framework.entity.BaseEntity;
import com.bakery.framework.entity.EntityStatus;

@NoRepositoryBean
public interface BaseRepository<E extends BaseEntity>
        extends JpaRepository<E, UUID>, JpaSpecificationExecutor<E> {

    List<E> findAllByStatus(EntityStatus status);

    Optional<E> findByIdAndStatus(UUID id, EntityStatus status);
}
