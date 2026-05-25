package com.loopers.interfaces.api.brand;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.brand.BrandCreateInfo;
import com.loopers.application.brand.BrandFacade;
import com.loopers.interfaces.api.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller implements BrandAdminV1ApiSpec {

    private final BrandFacade brandFacade;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BrandAdminV1Dto.CreateResponse> createBrand(@Valid @RequestBody BrandAdminV1Dto.CreateRequest request) {
        BrandCreateInfo brandCreateInfo = brandFacade.createBrand(request.name(), request.description());

        return ApiResponse.success(BrandAdminV1Dto.CreateResponse.from(brandCreateInfo));
    }
}
