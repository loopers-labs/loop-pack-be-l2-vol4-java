package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.domain.product.SortOption;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.dto.ProductV1Response;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductV1Controller implements ProductV1ApiSpec {

    private final ProductFacade productFacade;

    @GetMapping
    @Override
    public ApiResponse<Page<ProductV1Response>> search(
        @RequestParam(required = false) Long brandId,
        @RequestParam(required = false) String sort,
        Pageable pageable
    ) {
        Page<ProductV1Response> products = productFacade.search(brandId, SortOption.from(sort), pageable)
            .map(ProductV1Response::from);
        return ApiResponse.success(products);
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Response> getProduct(@PathVariable Long productId) {
        return ApiResponse.success(ProductV1Response.from(productFacade.getProductDetail(productId)));
    }
}
