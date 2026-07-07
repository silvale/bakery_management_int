package com.bakery.api.framework.services;

import com.bakery.api.framework.EntityRevisionLog;
import com.bakery.api.framework.enums.CommandAction;
import com.bakery.api.framework.repositories.EntityRevisionLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Ghi append-only history snapshot cho mọi thay đổi entity.
 * Luôn chạy trong transaction hiện tại (REQUIRED).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityHistoryService {

    private final EntityRevisionLogRepository revisionLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Ghi 1 revision entry.
     *
     * @param entityType       Tên entity (vd: "Ingredient")
     * @param entityId         UUID của entity trong bảng chính
     * @param action           CREATE / UPDATE / DELETE
     * @param commandRequestId UUID của CommandRequest đã trigger (nullable)
     * @param before           Object state trước thay đổi (null nếu CREATE)
     * @param after            Object state sau thay đổi (null nếu DELETE)
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(
            String entityType,
            UUID entityId,
            CommandAction action,
            UUID commandRequestId,
            Object before,
            Object after) {

        EntityRevisionLog log = EntityRevisionLog.builder()
            .entityType(entityType)
            .entityId(entityId)
            .action(action)
            .commandRequestId(commandRequestId)
            .snapshotBefore(toJson(before))
            .snapshotAfter(toJson(after))
            .build();

        revisionLogRepository.save(log);
    }

    /** Lấy toàn bộ history của 1 entity, mới nhất trước */
    @Transactional(readOnly = true)
    public List<EntityRevisionLog> getHistory(String entityType, UUID entityId) {
        return revisionLogRepository
            .findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }

    /** Paged history */
    @Transactional(readOnly = true)
    public List<EntityRevisionLog> getHistory(String entityType, UUID entityId, int page, int size) {
        return revisionLogRepository
            .findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                entityType, entityId, PageRequest.of(page, size))
            .getContent();
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Không serialize được object thành JSON: {}", e.getMessage());
            return "{\"error\": \"serialize_failed\"}";
        }
    }
}
