package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductListInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductFacade productFacade;

    @GetMapping
    public ApiResponse<PageResponse<ProductV1Dto.ProductResponse>> getProducts(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "latest") String sort
    ) {
        PageResult<ProductListInfo> products = productFacade.getProducts(page, size, brandId, sort);
        return ApiResponse.success(PageResponse.from(products.map(ProductV1Dto.ProductResponse::from)));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(@PathVariable Long productId) {
        ProductDetailInfo info = productFacade.getProduct(productId);
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info));
    }
}
