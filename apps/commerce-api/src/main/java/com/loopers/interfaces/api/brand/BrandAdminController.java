package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandAdminFacade;
import com.loopers.domain.brand.BrandAdminService;
import com.loopers.domain.brand.BrandModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api-admin/v1/brands")
@RequiredArgsConstructor
public class BrandAdminController {

    private final BrandAdminService brandAdminService;
    private final BrandAdminFacade brandAdminFacade;

    @PostMapping
    public ApiResponse<Long> registerBrand(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @RequestBody BrandAdminDto.RegisterBrandRequest request
    ) {
        validateAdmin(ldap);
        return ApiResponse.success(brandAdminService.registerBrand(request.name()));
    }

    @GetMapping
    public ApiResponse<List<BrandAdminDto.BrandResponse>> getBrands(
            @RequestHeader("X-Loopers-Ldap") String ldap
    ) {
        validateAdmin(ldap);
        List<BrandModel> brands = brandAdminService.getBrands();
        return ApiResponse.success(brands.stream()
                .map(b -> new BrandAdminDto.BrandResponse(b.getId(), b.getName()))
                .toList());
    }

    @PutMapping("/{brandId}")
    public ApiResponse<Void> updateBrand(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @PathVariable Long brandId,
            @RequestBody BrandAdminDto.UpdateBrandRequest request
    ) {
        validateAdmin(ldap);
        brandAdminService.updateBrand(brandId, request.name());
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> deleteBrand(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @PathVariable Long brandId
    ) {
        validateAdmin(ldap);
        brandAdminFacade.deleteBrand(brandId);
        return ApiResponse.success(null);
    }

    private void validateAdmin(String ldap) {
        if (!"loopers.admin".equals(ldap)) {
            throw new CoreException(ErrorType.NOT_FOUND, "권한이 없습니다."); // 또는 적절한 에러
        }
    }
}
