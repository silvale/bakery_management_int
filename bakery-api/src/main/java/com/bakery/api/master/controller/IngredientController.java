package com.bakery.api.master.controller;

import com.bakery.api.master.dto.IngredientRequest;
import com.bakery.api.master.dto.IngredientResponse;
import com.bakery.api.master.service.IngredientService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingredients")
@RequiredArgsConstructor
public class IngredientController extends BakeryAdminResource<IngredientRequest, IngredientResponse> {

    private final IngredientService service;

    @Override
    protected BakeryAdminService<IngredientRequest, IngredientResponse> getService() {
        return service;
    }
}
