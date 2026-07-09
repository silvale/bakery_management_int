package com.bakery.framework.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.util.MultiValueMap;

/**
 * Base service contract for all Bakery admin modules.
 *
 * @param <REQ> request DTO
 * @param <RES> response DTO
 */
public interface BakeryAdminService<REQ, RES> {

    Page<RES> findAll(MultiValueMap<String, String> params, Pageable pageable);

    List<RES> findAll();

    Optional<RES> findById(UUID id);

    RES create(REQ request);

    RES update(UUID id, REQ request);

    void delete(UUID id);

    RES approve(UUID id);

    RES reject(UUID id, String reason);
}
