package com.loopers.interfaces.api.product;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductSummaryInfo;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductFacade productFacade;

    @Override
    @GetMapping
    public ApiResponse<ProductV1Dto.PageResponse> readProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "latest") String sort,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<ProductSummaryInfo> productsSummaryInfo = productFacade.readProducts(brandId, ProductSortType.from(sort), page, size);

        return ApiResponse.success(ProductV1Dto.PageResponse.from(productsSummaryInfo));
    }

    @Override
    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.DetailResponse> readProduct(@PathVariable Long productId) {
        ProductDetailInfo productDetailInfo = productFacade.readProduct(productId);

        return ApiResponse.success(ProductV1Dto.DetailResponse.from(productDetailInfo));
    }
}
