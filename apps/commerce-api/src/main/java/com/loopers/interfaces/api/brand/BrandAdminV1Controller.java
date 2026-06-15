package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.brand.BrandCriteria;
import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.common.PageResult;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller implements BrandAdminV1ApiSpec {

    private final BrandApplicationService brandApplicationService;

    @GetMapping
    @Override
    public ApiResponse<BrandV1Dto.PageResponse> getBrands(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        PageResult<BrandInfo> result = brandApplicationService.getBrandPage(page, size);
        return ApiResponse.success(BrandV1Dto.PageResponse.from(result));
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandV1Dto.BrandResponse> getBrand(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @PathVariable("brandId") Long brandId
    ) {
        BrandInfo info = brandApplicationService.getBrand(brandId);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }

    @PostMapping
    @Override
    public ApiResponse<BrandV1Dto.BrandResponse> register(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @RequestBody BrandV1Dto.RegisterRequest request
    ) {
        BrandInfo info = brandApplicationService.register(
                new BrandCriteria.Register(request.name(), request.description()));
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(info));
    }

    @PutMapping("/{brandId}")
    @Override
    public ApiResponse<Void> modify(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @PathVariable("brandId") Long brandId,
            @RequestBody BrandV1Dto.ModifyRequest request
    ) {
        brandApplicationService.modify(
                new BrandCriteria.Modify(brandId, request.name(), request.description()));
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{brandId}")
    @Override
    public ApiResponse<Void> delete(
            @RequestHeader("X-Loopers-Ldap") String adminLdap,
            @PathVariable("brandId") Long brandId
    ) {
        brandApplicationService.delete(brandId);
        return ApiResponse.success(null);
    }
}
