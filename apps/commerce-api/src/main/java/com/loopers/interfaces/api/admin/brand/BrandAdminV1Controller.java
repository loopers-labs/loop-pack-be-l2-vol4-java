package com.loopers.interfaces.api.admin.brand;

import com.loopers.application.brand.BrandAdminFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.admin.brand.dto.BrandAdminV1Response;
import com.loopers.interfaces.api.auth.AdminUser;
import com.loopers.interfaces.api.auth.LdapAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api-admin/v1/brands")
@RequiredArgsConstructor
public class BrandAdminV1Controller implements BrandAdminV1ApiSpec {

    private final BrandAdminFacade brandAdminFacade;

    @GetMapping
    @Override
    public ApiResponse<Page<BrandAdminV1Response>> search(@LdapAdmin AdminUser admin, Pageable pageable) {
        return ApiResponse.success(brandAdminFacade.search(pageable).map(BrandAdminV1Response::from));
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandAdminV1Response> getBrand(@LdapAdmin AdminUser admin, @PathVariable Long brandId) {
        return ApiResponse.success(BrandAdminV1Response.from(brandAdminFacade.getBrand(brandId)));
    }
}
