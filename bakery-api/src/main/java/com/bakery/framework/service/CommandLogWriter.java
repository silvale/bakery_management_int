/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.framework.service;

import com.bakery.framework.entity.CommandRequest;
import com.bakery.framework.repository.CommandRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi activity log trong transaction REQUIRES_NEW — hoàn toàn tách biệt khỏi
 * outer transaction. Nếu ghi log thất bại, outer transaction không bị ảnh hưởng.
 *
 * <p>Phải là bean riêng (không phải inner method) để Spring AOP proxy hoạt động
 * và REQUIRES_NEW mới có hiệu lực.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommandLogWriter {

    private final CommandRequestRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(CommandRequest cmd) {
        try {
            repository.save(cmd);
        } catch (Exception ex) {
            log.warn("Không thể ghi activity log [{}:{}]: {}", cmd.getEntityName(), cmd.getAction(), ex.getMessage());
        }
    }
}
