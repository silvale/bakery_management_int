package com.bakery.api.modules.masterdata;

import com.bakery.api.framework.services.AdminOperationService;
import com.bakery.api.framework.services.EntityHistoryService;
import com.bakery.api.modules.masterdata.dtos.SemiProductRequest;
import com.bakery.api.modules.masterdata.dtos.SemiProductResponse;
import com.bakery.api.modules.masterdata.entities.SemiProduct;
import org.springframework.stereotype.Service;

@Service
public class SemiProductOperationService
        extends AdminOperationService<SemiProductRequest, SemiProductResponse, SemiProduct> {

    public SemiProductOperationService(
            SemiProductSupportService support,
            EntityHistoryService historyService) {
        super(support, historyService);
    }
}
