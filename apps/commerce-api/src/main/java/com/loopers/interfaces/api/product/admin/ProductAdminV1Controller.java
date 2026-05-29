package com.loopers.interfaces.api.product.admin;

import com.loopers.application.product.ProductAdminInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/products")
public class ProductAdminV1Controller implements ProductAdminV1ApiSpec {

    private final ProductFacade productFacade;

    @GetMapping
    @Override
    public ApiResponse<ProductAdminV1Dto.PageResponse> getProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<ProductAdminInfo> result = productFacade.getAllProductsForAdmin(brandId, page, size);
        return ApiResponse.success(ProductAdminV1Dto.PageResponse.from(result));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductAdminV1Dto.AdminProductDetail> getProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        ProductAdminInfo info = productFacade.getProductForAdmin(productId);
        return ApiResponse.success(ProductAdminV1Dto.AdminProductDetail.from(info));
    }
}
