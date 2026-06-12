package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller implements BrandAdminV1ApiSpec {

    private final BrandApplicationService brandApplicationService;

    @GetMapping
    public ApiResponse<PageResult<BrandV1Dto.BrandAdminResponse>> getBrands(Pageable pageable) {
        return ApiResponse.success(PageResult.from(brandApplicationService.getBrands(pageable).map(BrandV1Dto.BrandAdminResponse::from)));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandAdminResponse> getBrand(@PathVariable Long brandId) {
        return ApiResponse.success(BrandV1Dto.BrandAdminResponse.from(brandApplicationService.getBrand(brandId)));
    }

    @PostMapping
    public ApiResponse<BrandV1Dto.BrandAdminResponse> createBrand(@RequestBody BrandV1Dto.CreateBrandRequest request) {
        return ApiResponse.success(BrandV1Dto.BrandAdminResponse.from(brandApplicationService.createBrand(request.name(), request.description())));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandAdminResponse> updateBrand(
            @PathVariable Long brandId,
            @RequestBody BrandV1Dto.UpdateBrandRequest request
    ) {
        return ApiResponse.success(BrandV1Dto.BrandAdminResponse.from(brandApplicationService.updateBrand(brandId, request.name(), request.description())));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Object> deleteBrand(@PathVariable Long brandId) {
        brandApplicationService.deleteBrand(brandId);
        return ApiResponse.success();
    }
}
