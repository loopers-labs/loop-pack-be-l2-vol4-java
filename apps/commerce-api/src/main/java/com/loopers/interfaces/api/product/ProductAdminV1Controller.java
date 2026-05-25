package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductAdminFacade;
import com.loopers.application.product.ProductAdminInfo;
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
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller {

    private final ProductAdminFacade productAdminFacade;

    @GetMapping
    public ApiResponse<ProductAdminV1Dto.PageResponse> search(
        @LoginAdmin AdminUser admin,
        @RequestParam(required = false) Long brandId,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductAdminInfo> result = productAdminFacade.search(brandId, pageable);
        return ApiResponse.success(ProductAdminV1Dto.PageResponse.from(result));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductAdminV1Dto.Response> getProduct(
        @LoginAdmin AdminUser admin,
        @PathVariable Long productId
    ) {
        ProductAdminInfo info = productAdminFacade.getProduct(productId);
        return ApiResponse.success(ProductAdminV1Dto.Response.from(info));
    }
}
