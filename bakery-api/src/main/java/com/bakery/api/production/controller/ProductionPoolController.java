package com.bakery.api.production.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.bakery.api.production.dto.ProductionPoolDto.CloseRequest;
import com.bakery.api.production.dto.ProductionPoolDto.ConfirmRequest;
import com.bakery.api.production.dto.ProductionPoolDto.Created;
import com.bakery.api.production.dto.ProductionPoolDto.Doc;
import com.bakery.api.production.dto.ProductionPoolDto.PickRequest;
import com.bakery.api.production.dto.ProductionPoolDto.Pickable;
import com.bakery.api.production.dto.ProductionPoolDto.Source;
import com.bakery.api.production.dto.ProductionPoolDto.Pool;
import com.bakery.api.production.dto.ProductionPoolDto.SessionDetail;
import com.bakery.api.production.dto.ProductionPoolDto.SessionInfo;
import com.bakery.api.production.service.ProductionPickService;
import com.bakery.api.production.service.ProductionSessionService;
import com.bakery.framework.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 24: bảng tạm "Đang sản xuất": xem, phiếu lấy NL, phiên ngày. Quyền theo màn PROD_REQUESTS (VIEW / COMPLETE /
 * EXTRA_MATERIAL / APPROVE); như {@code ProductionRequestController}, user phải được gán kho Bánh chờ giao.
 */
@RestController
@RequestMapping("/api/v1/production")
@RequiredArgsConstructor
@com.bakery.api.auth.scope.RequireWarehouse(role = {com.bakery.api.master.entity.WarehouseRole.DELIVERY_HOLD})
public class ProductionPoolController {

    private final ProductionPickService picks;
    private final ProductionSessionService sessions;

    @GetMapping("/pool")
    @RequirePermission(screen = "PROD_REQUESTS", action = "VIEW")
    public Pool pool() { return picks.pool(); }

    @GetMapping("/pool/pickable")
    @RequirePermission(screen = "PROD_REQUESTS", action = "VIEW")
    public List<Pickable> pickable(@RequestParam(required = false) String q) { return picks.pickable(q); }

    @GetMapping("/pool/sources")
    @RequirePermission(screen = "PROD_REQUESTS", action = "VIEW")
    public List<Source> sources() { return picks.sources(); }

    @PostMapping("/pool/generate")
    @RequirePermission(screen = "PROD_REQUESTS", action = "COMPLETE")
    public Created generate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) { return picks.generate(date); }

    /** Danh sách phiếu PICK / RETURN / WRITE_OFF theo ngày (mặc định 7 ngày gần nhất) cho tab Phiếu lấy. */
    @GetMapping("/picks")
    @RequirePermission(screen = "PROD_REQUESTS", action = "VIEW")
    public List<Doc> listPicks(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return picks.list(from, to);
    }

    /** Quyền COMPLETE; lý do EXTRA_* cần thêm EXTRA_MATERIAL (kiểm trong service). */
    @PostMapping("/picks")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(screen = "PROD_REQUESTS", action = "COMPLETE")
    public Created createPick(@RequestBody PickRequest body) { return picks.create(body); }

    @PostMapping("/picks/{id}/confirm")
    @RequirePermission(screen = "PROD_REQUESTS", action = "COMPLETE")
    public Doc confirm(@PathVariable UUID id, @RequestBody ConfirmRequest body) { return picks.confirm(id, body); }

    @DeleteMapping("/picks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(screen = "PROD_REQUESTS", action = "COMPLETE")
    public void deletePick(@PathVariable UUID id) { picks.delete(id); }

    @GetMapping("/sessions")
    @RequirePermission(screen = "PROD_REQUESTS", action = "VIEW")
    public List<SessionInfo> sessions(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return sessions.list(from, to);
    }

    @GetMapping("/sessions/{id}")
    @RequirePermission(screen = "PROD_REQUESTS", action = "VIEW")
    public SessionDetail session(@PathVariable UUID id) { return sessions.get(id); }

    @PostMapping("/sessions/{id}/close")
    @RequirePermission(screen = "PROD_REQUESTS", action = "APPROVE")
    public SessionDetail close(@PathVariable UUID id, @RequestBody CloseRequest body) { return sessions.close(id, body); }

    @PostMapping("/sessions/{id}/reopen")
    @RequirePermission(screen = "PROD_REQUESTS", action = "APPROVE")
    public SessionInfo reopen(@PathVariable UUID id) { return sessions.reopen(id); }
}
