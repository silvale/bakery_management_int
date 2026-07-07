package com.bakery.api.modules.masterdata;

import com.bakery.api.framework.services.AdminOperationService;
import com.bakery.api.framework.services.EntityHistoryService;
import com.bakery.api.modules.masterdata.dtos.BranchRequest;
import com.bakery.api.modules.masterdata.dtos.BranchResponse;
import com.bakery.api.modules.masterdata.entities.Branch;
import org.springframework.stereotype.Service;

@Service
public class BranchOperationService
        extends AdminOperationService<BranchRequest, BranchResponse, Branch> {

    public BranchOperationService(BranchSupportService support, EntityHistoryService historyService) {
        super(support, historyService);
    }
}
