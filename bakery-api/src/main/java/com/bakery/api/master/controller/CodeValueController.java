package com.bakery.api.master.controller;

import com.bakery.api.master.dto.CodeValueRequest;
import com.bakery.api.master.dto.CodeValueResponse;
import com.bakery.api.master.service.CodeValueService;
import com.bakery.framework.controller.BakeryAdminResource;
import com.bakery.framework.service.BakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/code-values")
@RequiredArgsConstructor
public class CodeValueController extends BakeryAdminResource<CodeValueRequest, CodeValueResponse> {

    private final CodeValueService service;

    @Override
    protected BakeryAdminService<CodeValueRequest, CodeValueResponse> getService() {
        return service;
    }
}
