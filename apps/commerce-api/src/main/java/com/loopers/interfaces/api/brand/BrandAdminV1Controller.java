package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandAdminFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.pagination.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class BrandAdminV1Controller {

    private final BrandAdminFacade brandAdminFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BrandAdminV1Dto.BrandResponse> createBrand(@Valid @RequestBody BrandAdminV1Dto.CreateBrandRequest request) {
        BrandInfo info = brandAdminFacade.createBrand(request.toCommand());
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandAdminV1Dto.BrandResponse> getBrand(@PathVariable Long brandId) {
        BrandInfo info = brandAdminFacade.getBrand(brandId);
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @GetMapping
    public ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>> getBrands(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PageResult<BrandAdminV1Dto.BrandResponse> brands = brandAdminFacade.getBrands(page, size)
            .map(BrandAdminV1Dto.BrandResponse::from);
        return ApiResponse.success(PageResponse.from(brands));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandAdminV1Dto.BrandResponse> updateBrand(
        @PathVariable Long brandId,
        @Valid @RequestBody BrandAdminV1Dto.UpdateBrandRequest request
    ) {
        BrandInfo info = brandAdminFacade.updateBrand(request.toCommand(brandId));
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Object> deleteBrand(@PathVariable Long brandId) {
        brandAdminFacade.deleteBrand(brandId);
        return ApiResponse.success();
    }
}
