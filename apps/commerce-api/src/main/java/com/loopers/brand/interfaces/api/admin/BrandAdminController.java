package com.loopers.brand.interfaces.api.admin;

import com.loopers.brand.application.BrandFacade;
import com.loopers.brand.application.BrandInfo;
import com.loopers.brand.interfaces.api.BrandResponse;
import com.loopers.brand.interfaces.api.CreateBrandRequest;
import com.loopers.brand.interfaces.api.UpdateBrandRequest;
import com.loopers.common.interfaces.api.AdminAuth;
import com.loopers.common.interfaces.api.ApiResponse;
import com.loopers.common.interfaces.api.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminController {

    private final BrandFacade brandFacade;

    @GetMapping
    public ApiResponse<PagedResponse<BrandResponse>> getBrands(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size) {
        AdminAuth.verify(ldap);
        Page<BrandInfo> result = brandFacade.getBrands(page, size);
        return ApiResponse.success(PagedResponse.from(result.map(BrandResponse::from)));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandResponse> getBrand(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap, @PathVariable("brandId") Long brandId) {
        AdminAuth.verify(ldap);
        return ApiResponse.success(BrandResponse.from(brandFacade.getBrand(brandId)));
    }

    @PostMapping
    public ApiResponse<BrandResponse> createBrand(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap,
        @RequestBody CreateBrandRequest request) {
        AdminAuth.verify(ldap);
        BrandInfo info = brandFacade.createBrand(request.name(), request.description());
        return ApiResponse.success(BrandResponse.from(info));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandResponse> updateBrand(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap,
        @PathVariable("brandId") Long brandId,
        @RequestBody UpdateBrandRequest request) {
        AdminAuth.verify(ldap);
        BrandInfo info = brandFacade.updateBrand(brandId, request.name(), request.description());
        return ApiResponse.success(BrandResponse.from(info));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> deleteBrand(
        @RequestHeader(AdminAuth.LDAP_HEADER) String ldap, @PathVariable("brandId") Long brandId) {
        AdminAuth.verify(ldap);
        brandFacade.deleteBrand(brandId);
        return ApiResponse.success(null);
    }
}
