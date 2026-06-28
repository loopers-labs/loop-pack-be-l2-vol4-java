package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller {

    private final BrandFacade brandFacade;

    @GetMapping
    public ApiResponse<Page<BrandV1Dto.BrandResponse>> getBrands(Pageable pageable) {
        return ApiResponse.success(brandFacade.getBrands(pageable).map(BrandV1Dto.BrandResponse::from));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> getBrand(@PathVariable Long brandId) {
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(brandFacade.getBrand(brandId)));
    }

    @PostMapping
    public ApiResponse<BrandV1Dto.BrandResponse> createBrand(
        @RequestBody BrandV1Dto.CreateBrandRequest request
    ) {
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(
            brandFacade.createBrand(request.name(), request.description(), request.imageUrl())
        ));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> updateBrand(
        @PathVariable Long brandId,
        @RequestBody BrandV1Dto.UpdateBrandRequest request
    ) {
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(
            brandFacade.updateBrand(brandId, request.name(), request.description(), request.imageUrl())
        ));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> deleteBrand(@PathVariable Long brandId) {
        brandFacade.deleteBrand(brandId);
        return ApiResponse.success(null);
    }
}
