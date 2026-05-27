package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.domain.brand.BrandService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.user.AdminAuth;
import com.loopers.interfaces.api.user.AuthHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1")
public class BrandAdminV1Controller {

    private final BrandService brandService;
    private final BrandFacade brandFacade;

    @GetMapping("/brands")
    public ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>> getAllBrands(
        @RequestHeader(AuthHeaders.LDAP) String ldap,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        AdminAuth.validate(ldap);
        return ApiResponse.success(PageResponse.from(
            brandService.findAll(PageRequest.of(page, size)).map(BrandAdminV1Dto.BrandResponse::from)
        ));
    }

    @GetMapping("/brands/{brandId}")
    public ApiResponse<BrandAdminV1Dto.BrandResponse> getBrand(
        @RequestHeader(AuthHeaders.LDAP) String ldap,
        @PathVariable Long brandId
    ) {
        AdminAuth.validate(ldap);
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(brandService.getById(brandId)));
    }

    @PostMapping("/brands")
    public ApiResponse<BrandAdminV1Dto.BrandResponse> createBrand(
        @RequestHeader(AuthHeaders.LDAP) String ldap,
        @RequestBody BrandAdminV1Dto.CreateBrandRequest request
    ) {
        AdminAuth.validate(ldap);
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(brandService.create(request.name())));
    }

    @PutMapping("/brands/{brandId}")
    public ApiResponse<BrandAdminV1Dto.BrandResponse> updateBrand(
        @RequestHeader(AuthHeaders.LDAP) String ldap,
        @PathVariable Long brandId,
        @RequestBody BrandAdminV1Dto.UpdateBrandRequest request
    ) {
        AdminAuth.validate(ldap);
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(brandService.update(brandId, request.name())));
    }

    @DeleteMapping("/brands/{brandId}")
    public ApiResponse<Void> deleteBrand(
        @RequestHeader(AuthHeaders.LDAP) String ldap,
        @PathVariable Long brandId
    ) {
        AdminAuth.validate(ldap);
        brandFacade.deleteBrand(brandId);
        return ApiResponse.success(null);
    }

}
