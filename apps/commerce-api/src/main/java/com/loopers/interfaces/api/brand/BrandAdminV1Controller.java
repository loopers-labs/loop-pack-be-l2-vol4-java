package com.loopers.interfaces.api.brand;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.brand.BrandCreateInfo;
import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.brand.BrandUpdateInfo;
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

    @Override
    @PutMapping("/{brandId}")
    public ApiResponse<BrandAdminV1Dto.UpdateResponse> updateBrand(
        @PathVariable Long brandId,
        @Valid @RequestBody BrandAdminV1Dto.UpdateRequest request
    ) {
        BrandUpdateInfo brandUpdateInfo = brandFacade.updateBrand(brandId, request.name(), request.description());

        return ApiResponse.success(BrandAdminV1Dto.UpdateResponse.from(brandUpdateInfo));
    }

    @Override
    @GetMapping("/{brandId}")
    public ApiResponse<BrandAdminV1Dto.DetailResponse> readBrand(@PathVariable Long brandId) {
        BrandInfo brandInfo = brandFacade.readBrand(brandId);

        return ApiResponse.success(BrandAdminV1Dto.DetailResponse.from(brandInfo));
    }

    @Override
    @GetMapping
    public ApiResponse<BrandAdminV1Dto.PageResponse> readBrands(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<BrandInfo> brandsInfo = brandFacade.readBrands(page, size);

        return ApiResponse.success(BrandAdminV1Dto.PageResponse.from(brandsInfo));
    }

    @Override
    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> deleteBrand(@PathVariable Long brandId) {
        brandFacade.deleteBrand(brandId);

        return ApiResponse.success();
    }
}
