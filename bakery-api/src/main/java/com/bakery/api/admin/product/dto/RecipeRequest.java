package com.bakery.api.admin.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class RecipeRequest {

    @NotNull
    private LocalDate effectiveDate;

    private String note;

    @NotEmpty
    @Valid
    private List<RecipeLineRequest> lines;
}
