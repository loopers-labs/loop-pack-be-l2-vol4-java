package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
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

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandV1Controller {

    private final BrandFacade brandFacade;

    @GetMapping
    public ApiResponse<Page<BrandV1Dto.BrandAdminResponse>> getBrands(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<BrandInfo> brands = brandFacade.getBrands(page, size);
        return ApiResponse.success(brands.map(BrandV1Dto.BrandAdminResponse::from));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandAdminResponse> getBrand(
        @PathVariable Long brandId
    ) {
        BrandInfo info = brandFacade.getBrand(brandId);
        return ApiResponse.success(BrandV1Dto.BrandAdminResponse.from(info));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BrandV1Dto.BrandResponse> register(
        @RequestBody BrandV1Dto.RegisterRequest request
    ) {
        BrandInfo info = brandFacade.register(request.name());
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandAdminResponse> updateBrand(
        @PathVariable Long brandId,
        @RequestBody BrandV1Dto.UpdateRequest request
    ) {
        BrandInfo info = brandFacade.updateBrand(brandId, request.name());
        return ApiResponse.success(BrandV1Dto.BrandAdminResponse.from(info));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> deleteBrand(@PathVariable Long brandId) {
        brandFacade.deleteBrand(brandId);
        return ApiResponse.success(null);
    }
}
