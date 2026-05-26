package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller {

    private final BrandFacade brandFacade;

    @GetMapping
    public ApiResponse<List<BrandV1Dto.BrandResponse>> getBrands(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        List<BrandInfo> infos = brandFacade.getBrands(page, size);
        List<BrandV1Dto.BrandResponse> responses = infos.stream()
            .map(BrandV1Dto.BrandResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> getBrand(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @PathVariable Long brandId
    ) {
        BrandInfo info = brandFacade.getBrand(brandId);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BrandV1Dto.BrandResponse> createBrand(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @RequestBody BrandV1Dto.CreateBrandRequest request
    ) {
        BrandInfo info = brandFacade.createBrand(request.name(), request.description());
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> updateBrand(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @PathVariable Long brandId,
        @RequestBody BrandV1Dto.UpdateBrandRequest request
    ) {
        BrandInfo info = brandFacade.updateBrand(brandId, request.name(), request.description());
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> deleteBrand(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @PathVariable Long brandId
    ) {
        brandFacade.deleteBrand(brandId);
        return ApiResponse.success(null);
    }
}
