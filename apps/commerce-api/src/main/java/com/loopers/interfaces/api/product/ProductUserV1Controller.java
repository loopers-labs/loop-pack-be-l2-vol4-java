package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
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
@RequestMapping("/api/v1/products")
public class ProductUserV1Controller {

    private final ProductFacade productFacade;

    @GetMapping
    public ApiResponse<Page<ProductV1Dto.ProductUserResponse>> getProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "latest") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<ProductInfo> products = productFacade.getProducts(brandId, sort, page, size);
        return ApiResponse.success(products.map(ProductV1Dto.ProductUserResponse::from));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductUserResponse> getProduct(
        @PathVariable Long productId
    ) {
        ProductInfo info = productFacade.getProduct(productId);
        return ApiResponse.success(ProductV1Dto.ProductUserResponse.from(info));
    }
}
