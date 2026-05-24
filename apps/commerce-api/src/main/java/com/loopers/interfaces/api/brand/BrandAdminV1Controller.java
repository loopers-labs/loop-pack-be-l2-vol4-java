package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandAdminFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller {

    private final BrandAdminFacade brandAdminFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BrandV1Dto.BrandResponse> createBrand(@RequestBody BrandAdminV1Dto.CreateBrandRequest request) {
        BrandInfo info = brandAdminFacade.createBrand(request.toCommand());
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> getBrand(@PathVariable Long brandId) {
        BrandInfo info = brandAdminFacade.getBrand(brandId);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }

    @GetMapping
    public ApiResponse<PageResponse<BrandV1Dto.BrandResponse>> getBrands(@PageableDefault(size = 20) Pageable pageable) {
        Page<BrandV1Dto.BrandResponse> brands = brandAdminFacade.getBrands(pageable)
            .map(BrandV1Dto.BrandResponse::from);
        return ApiResponse.success(PageResponse.from(brands));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> updateBrand(
        @PathVariable Long brandId,
        @RequestBody BrandAdminV1Dto.UpdateBrandRequest request
    ) {
        BrandInfo info = brandAdminFacade.updateBrand(request.toCommand(brandId));
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }
}
