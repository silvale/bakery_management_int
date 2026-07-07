package com.bakery.api.modules.masterdata;

import com.bakery.api.modules.masterdata.dtos.ProductPriceRequest;
import com.bakery.api.modules.masterdata.dtos.ProductPriceResponse;
import com.bakery.api.framework.services.AdminCommandService;
import com.bakery.api.framework.CommandRequest;
import com.bakery.api.modules.masterdata.entities.ProductPrice;
import com.bakery.api.framework.repositories.CommandRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProductPriceCommandService
        extends AdminCommandService<ProductPriceRequest, ProductPriceResponse, ProductPrice> {

    public ProductPriceCommandService(
            ProductPriceSupportService support,
            ProductPriceOperationService operationService,
            CommandRequestRepository commandRequestRepository,
            ObjectMapper objectMapper) {
        super(support, operationService, commandRequestRepository, objectMapper);
    }

    @Override
    protected Class<ProductPriceRequest> requestClass() {
        return ProductPriceRequest.class;
    }

    /** Lịch sử giá là bất biến — không cho phép update */
    @Override
    public CommandRequest submitUpdate(UUID entityId, ProductPriceRequest request, String note) {
        throw new UnsupportedOperationException("Cập nhật giá không được phép. Hãy tạo version giá mới.");
    }

    /** Lịch sử giá là bất biến — không cho phép delete */
    @Override
    public CommandRequest submitDelete(UUID entityId, String note) {
        throw new UnsupportedOperationException("Xóa giá không được phép. Lịch sử giá là bất biến.");
    }
}
