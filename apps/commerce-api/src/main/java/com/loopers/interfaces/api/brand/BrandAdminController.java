package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/brands")
public class BrandAdminController {

    private final BrandFacade brandFacade;

    @GetMapping
    public ApiResponse<List<BrandDto.BrandResponse>> getBrands() {
        List<BrandInfo> brands = brandFacade.getAllBrands();
        return ApiResponse.success(brands.stream().map(BrandDto.BrandResponse::from).toList());
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandDto.BrandResponse> getBrand(@PathVariable Long brandId) {
        BrandInfo brand = brandFacade.getBrand(brandId);
        return ApiResponse.success(BrandDto.BrandResponse.from(brand));
    }

    @PostMapping
    public ApiResponse<BrandDto.BrandResponse> createBrand(@RequestBody BrandDto.CreateBrandRequest request) {
        BrandInfo brand = brandFacade.createBrand(request.name());
        return ApiResponse.success(BrandDto.BrandResponse.from(brand));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandDto.BrandResponse> updateBrand(
        @PathVariable Long brandId,
        @RequestBody BrandDto.UpdateBrandRequest request
    ) {
        BrandInfo brand = brandFacade.updateBrand(brandId, request.name());
        return ApiResponse.success(BrandDto.BrandResponse.from(brand));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> deleteBrand(@PathVariable Long brandId) {
        brandFacade.deleteBrand(brandId);
        return ApiResponse.success(null);
    }
}
