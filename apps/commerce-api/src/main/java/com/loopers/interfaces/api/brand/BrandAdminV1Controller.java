package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandAdminFacade;
import com.loopers.application.brand.BrandAdminInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AdminUser;
import com.loopers.interfaces.api.auth.LoginAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller {

    private final BrandAdminFacade brandAdminFacade;

    @GetMapping
    public ApiResponse<BrandAdminV1Dto.PageResponse> search(
        @LoginAdmin AdminUser admin,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<BrandAdminInfo> result = brandAdminFacade.search(pageable);
        return ApiResponse.success(BrandAdminV1Dto.PageResponse.from(result));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandAdminV1Dto.Response> getBrand(
        @LoginAdmin AdminUser admin,
        @PathVariable Long brandId
    ) {
        BrandAdminInfo info = brandAdminFacade.getBrand(brandId);
        return ApiResponse.success(BrandAdminV1Dto.Response.from(info));
    }
}
