package com.bakery.api.master.service;

import com.bakery.api.master.dto.CodeValueRequest;
import com.bakery.api.master.dto.CodeValueResponse;
import com.bakery.api.master.entity.CodeValue;
import com.bakery.api.master.repository.CodeValueRepository;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CodeValueService extends AbstractBakeryAdminService<CodeValue, CodeValueRequest, CodeValueResponse> {

    private final CodeValueRepository repository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;

    @Override protected BaseRepository<CodeValue> getRepository() { return repository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "CodeValue"; }
    @Override protected boolean isAutoApprove() { return true; }

    @Override
    protected CodeValue toEntity(CodeValueRequest req) {
        CodeValue e = new CodeValue();
        e.setGroupKey(req.groupKey());
        e.setCode(req.code());
        e.setName(req.name());
        e.setSortOrder(req.sortOrder());
        return e;
    }

    @Override
    protected void applyUpdate(CodeValue e, CodeValueRequest req) {
        e.setGroupKey(req.groupKey());
        e.setCode(req.code());
        e.setName(req.name());
        e.setSortOrder(req.sortOrder());
    }

    @Override
    protected CodeValueResponse toResponse(CodeValue e) {
        CodeValueResponse r = new CodeValueResponse();
        r.applyFrom(e);
        r.setGroupKey(e.getGroupKey());
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setSortOrder(e.getSortOrder());
        return r;
    }
}
