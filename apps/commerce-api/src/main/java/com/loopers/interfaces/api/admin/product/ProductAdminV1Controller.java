package com.loopers.interfaces.api.admin.product;

import com.loopers.application.product.ProductAdminFacade;
import com.loopers.domain.product.SortOption;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.admin.product.dto.ProductAdminV1Response;
import com.loopers.interfaces.api.auth.AdminUser;
import com.loopers.interfaces.api.auth.LdapAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api-admin/v1/products")
@RequiredArgsConstructor
public class ProductAdminV1Controller implements ProductAdminV1ApiSpec {

    private final ProductAdminFacade productAdminFacade;

    @GetMapping
    @Override
    public ApiResponse<Page<ProductAdminV1Response>> search(
        @LdapAdmin AdminUser admin,
        @RequestParam(required = false) Long brandId,
        @RequestParam(required = false) String sort,
        Pageable pageable
    ) {
        Page<ProductAdminV1Response> products = productAdminFacade.search(brandId, SortOption.from(sort), pageable)
            .map(ProductAdminV1Response::from);
        return ApiResponse.success(products);
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductAdminV1Response> getProduct(@LdapAdmin AdminUser admin, @PathVariable Long productId) {
        return ApiResponse.success(ProductAdminV1Response.from(productAdminFacade.getProduct(productId)));
    }
}
