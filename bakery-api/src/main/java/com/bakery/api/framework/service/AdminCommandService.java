package com.bakery.api.framework.service;

import com.bakery.api.framework.dto.BakeryBaseResponse;
import com.bakery.api.framework.exception.AdminEntityNotFoundException;
import com.bakery.api.framework.exception.AdminValidationException;
import com.bakery.common.entity.BaseAdminEntity;
import com.bakery.common.entity.CommandRequest;
import com.bakery.common.entity.enums.CommandAction;
import com.bakery.common.entity.enums.CommandStatus;
import com.bakery.common.repository.CommandRequestRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Quản lý vòng đời CommandRequest: submit → approve/reject.
 *
 * Flow:
 *   submit(CREATE/UPDATE/DELETE) → tạo CommandRequest PENDING
 *   approve(commandId)           → thực thi operation + chuyển APPROVED
 *   reject(commandId, reason)    → chuyển REJECTED, không thực thi
 *
 * Lưu ý: approve() cần biết REQ type để deserialize payload.
 * Concrete class phải cung cấp requestClass().
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AdminCommandService<REQ, RES extends BakeryBaseResponse, E extends BaseAdminEntity> {

    protected final AdminEntitySupportService<REQ, RES, E> support;
    protected final AdminOperationService<REQ, RES, E>     operationService;
    protected final CommandRequestRepository               commandRequestRepository;
    protected final ObjectMapper                           objectMapper;

    /** Class của Request DTO — dùng để deserialize payload khi approve */
    protected abstract Class<REQ> requestClass();

    // ── Submit ────────────────────────────────────────────────

    @Transactional
    public CommandRequest submitCreate(REQ request, String note) {
        guardNoPendingForCreate();
        return saveCommand(null, CommandAction.CREATE, request, note);
    }

    @Transactional
    public CommandRequest submitUpdate(UUID entityId, REQ request, String note) {
        // Đảm bảo entity tồn tại
        support.findById(entityId)
            .orElseThrow(() -> new AdminEntityNotFoundException(support.entityType(), entityId));
        guardNoPending(entityId);
        return saveCommand(entityId, CommandAction.UPDATE, request, note);
    }

    @Transactional
    public CommandRequest submitDelete(UUID entityId, String note) {
        support.findById(entityId)
            .orElseThrow(() -> new AdminEntityNotFoundException(support.entityType(), entityId));
        guardNoPending(entityId);
        return saveCommand(entityId, CommandAction.DELETE, null, note);
    }

    // ── Approve ───────────────────────────────────────────────

    @Transactional
    public E approve(UUID commandId) {
        CommandRequest cmd = findPendingCommand(commandId);

        E result = switch (cmd.getAction()) {
            case CREATE -> {
                REQ req = deserializePayload(cmd.getPayload());
                E entity = operationService.executeCreate(req, commandId);
                // fill entityId sau khi create
                cmd.setEntityId(entity.getId());
                yield entity;
            }
            case UPDATE -> {
                REQ req = deserializePayload(cmd.getPayload());
                yield operationService.executeUpdate(cmd.getEntityId(), req, commandId);
            }
            case DELETE -> {
                operationService.executeDelete(cmd.getEntityId(), commandId);
                yield null;
            }
        };

        cmd.setStatus(CommandStatus.APPROVED);
        cmd.setReviewedAt(OffsetDateTime.now());
        commandRequestRepository.save(cmd);

        log.info("[{}] Command {} APPROVED (action={})", support.entityType(), commandId, cmd.getAction());
        return result;
    }

    // ── Reject ────────────────────────────────────────────────

    @Transactional
    public CommandRequest reject(UUID commandId, String reason) {
        CommandRequest cmd = findPendingCommand(commandId);

        cmd.setStatus(CommandStatus.REJECTED);
        cmd.setReviewedAt(OffsetDateTime.now());
        cmd.setRejectReason(reason);
        commandRequestRepository.save(cmd);

        log.info("[{}] Command {} REJECTED", support.entityType(), commandId);
        return cmd;
    }

    // ── Queries ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<CommandRequest> listPending(int page, int size) {
        return commandRequestRepository.findByEntityTypeAndStatus(
            support.entityType(),
            CommandStatus.PENDING,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    @Transactional(readOnly = true)
    public Page<CommandRequest> listRejected(int page, int size) {
        return commandRequestRepository.findByEntityTypeAndStatus(
            support.entityType(),
            CommandStatus.REJECTED,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "reviewedAt"))
        );
    }

    // ── Helpers ───────────────────────────────────────────────

    private CommandRequest saveCommand(UUID entityId, CommandAction action, REQ request, String note) {
        String payload = request != null ? serializePayload(request) : "{}";
        CommandRequest cmd = CommandRequest.builder()
            .entityType(support.entityType())
            .entityId(entityId)
            .action(action)
            .status(CommandStatus.PENDING)
            .payload(payload)
            .note(note)
            .build();
        return commandRequestRepository.save(cmd);
    }

    private CommandRequest findPendingCommand(UUID commandId) {
        CommandRequest cmd = commandRequestRepository.findById(commandId)
            .orElseThrow(() -> new AdminEntityNotFoundException("CommandRequest", commandId));
        if (cmd.getStatus() != CommandStatus.PENDING) {
            throw new IllegalStateException(
                "Command " + commandId + " không ở trạng thái PENDING (hiện tại: " + cmd.getStatus() + ")");
        }
        return cmd;
    }

    private void guardNoPending(UUID entityId) {
        if (commandRequestRepository.existsByEntityIdAndStatus(entityId, CommandStatus.PENDING)) {
            throw new AdminValidationException(
                "Entity " + entityId + " đang có pending command. Vui lòng approve/reject trước.");
        }
    }

    private void guardNoPendingForCreate() {
        // CREATE không có entityId nên không cần guard
    }

    private String serializePayload(REQ request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không serialize được request: " + e.getMessage(), e);
        }
    }

    private REQ deserializePayload(String payload) {
        try {
            return objectMapper.readValue(payload, requestClass());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không deserialize được payload: " + e.getMessage(), e);
        }
    }
}
