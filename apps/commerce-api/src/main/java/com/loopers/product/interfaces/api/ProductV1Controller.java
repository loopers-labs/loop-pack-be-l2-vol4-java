package com.loopers.product.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.product.application.ProductQueryService;
import com.loopers.product.domain.ProductSortOption;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductQueryService productQueryService;

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Response.Detail> get(@PathVariable Long productId) {
        return ApiResponse.success(ProductV1Response.Detail.from(productQueryService.getProduct(productId)));
    }

    @GetMapping
    @Override
    public ApiResponse<List<ProductV1Response.Detail>> getAll(
        @RequestParam(defaultValue = "LATEST") ProductSortOption sort
    ) {
        List<ProductV1Response.Detail> responses = productQueryService.getProducts(sort).stream()
                .map(ProductV1Response.Detail::from)
                .toList();
        return ApiResponse.success(responses);
    }
}
