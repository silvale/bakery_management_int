package com.bakery.api.admin.ingredientprice;

import com.bakery.api.admin.ingredientprice.dto.IngredientPriceRequest;
import com.bakery.api.admin.ingredientprice.dto.IngredientPriceResponse;
import com.bakery.api.framework.service.AdminCommandService;
import com.bakery.common.entity.CommandRequest;
import com.bakery.common.entity.IngredientPrice;
import com.bakery.common.repository.CommandRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class IngredientPriceCommandService
        extends AdminCommandService<IngredientPriceRequest, IngredientPriceResponse, IngredientPrice> {

    public IngredientPriceCommandService(
            IngredientPriceSupportService support,
            IngredientPriceOperationService operationService,
            CommandRequestRepository commandRequestRepository,
            ObjectMapper objectMapper) {
        super(support, operationService, commandRequestRepository, objectMapper);
    }

    @Override
    protected Class<IngredientPriceRequest> requestClass() {
        return IngredientPriceRequest.class;
    }

    /** Giá không được update — chỉ tạo version mới */
    @Override
    public CommandRequest submitUpdate(UUID entityId, IngredientPriceRequest request, String note) {
        throw new UnsupportedOperationException("Cập nhật giá không được phép. Hãy tạo version giá mới.");
    }

    /** Giá không được xóa — lịch sử là bất biến */
    @Override
    public CommandRequest submitDelete(UUID entityId, String note) {
        throw new UnsupportedOperationException("Xóa giá không được phép. Lịch sử giá là bất biến.");
    }
}
