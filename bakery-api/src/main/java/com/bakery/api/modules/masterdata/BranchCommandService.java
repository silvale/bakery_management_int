package com.bakery.api.modules.masterdata;

import com.bakery.api.framework.repositories.CommandRequestRepository;
import com.bakery.api.framework.services.AdminCommandService;
import com.bakery.api.modules.masterdata.dtos.BranchRequest;
import com.bakery.api.modules.masterdata.dtos.BranchResponse;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class BranchCommandService
        extends AdminCommandService<BranchRequest, BranchResponse, Branch> {

    public BranchCommandService(
            BranchSupportService support,
            BranchOperationService operationService,
            CommandRequestRepository commandRequestRepository,
            ObjectMapper objectMapper) {
        super(support, operationService, commandRequestRepository, objectMapper);
    }

    @Override
    protected Class<BranchRequest> requestClass() { return BranchRequest.class; }
}
