package com.loopers.brand.interfaces;

import com.loopers.brand.application.BrandFacade;
import com.loopers.brand.application.BrandInfo;
import com.loopers.support.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/brands")
public class AdminBrandV1Controller {

    private final BrandFacade brandFacade;

    @PostMapping
    public ApiResponse<AdminBrandV1Dto.BrandResponse> createBrand(
        @RequestBody AdminBrandV1Dto.CreateRequest request
    ) {
        BrandInfo info = brandFacade.createBrand(request.name(), request.description());
        return ApiResponse.success(AdminBrandV1Dto.BrandResponse.from(info));
    }

    @PatchMapping("/{brandId}")
    public ApiResponse<AdminBrandV1Dto.BrandResponse> updateBrand(
        @PathVariable Long brandId,
        @RequestBody AdminBrandV1Dto.UpdateRequest request
    ) {
        BrandInfo info = brandFacade.updateBrand(brandId, request.name(), request.description());
        return ApiResponse.success(AdminBrandV1Dto.BrandResponse.from(info));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> deleteBrand(@PathVariable Long brandId) {
        brandFacade.deleteBrand(brandId);
        return ApiResponse.success(null);
    }
}
