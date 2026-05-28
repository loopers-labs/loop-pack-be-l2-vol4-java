package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
public class BrandAdminV1Controller {

    private final BrandFacade brandFacade;

    @GetMapping
    public ApiResponse<Page<BrandV1Dto.BrandAdminResponse>> getBrands(Pageable pageable) {
        return ApiResponse.success(brandFacade.getBrands(pageable).map(BrandV1Dto.BrandAdminResponse::from));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandAdminResponse> getBrand(@PathVariable Long brandId) {
        return ApiResponse.success(BrandV1Dto.BrandAdminResponse.from(brandFacade.getBrand(brandId)));
    }

    @PostMapping
    public ApiResponse<BrandV1Dto.BrandAdminResponse> createBrand(@RequestBody BrandV1Dto.CreateBrandRequest request) {
        return ApiResponse.success(BrandV1Dto.BrandAdminResponse.from(brandFacade.createBrand(request.name(), request.description())));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandAdminResponse> updateBrand(
            @PathVariable Long brandId,
            @RequestBody BrandV1Dto.UpdateBrandRequest request
    ) {
        return ApiResponse.success(BrandV1Dto.BrandAdminResponse.from(brandFacade.updateBrand(brandId, request.name(), request.description())));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Object> deleteBrand(@PathVariable Long brandId) {
        brandFacade.deleteBrand(brandId);
        return ApiResponse.success();
    }
}
