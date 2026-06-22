package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductFacade productFacade;

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductResponse> getProduct(
        @PathVariable(value = "productId") Long productId
    ) {
        ProductInfo info = productFacade.getProduct(productId);
        ProductV1Dto.ProductResponse response = ProductV1Dto.ProductResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductV1Dto.ProductResponse>> getProducts(
        @RequestParam(value = "brandId", required = false) Long brandId,
        @RequestParam(value = "sort", required = false, defaultValue = "latest") String sort,
        Pageable pageable
    ) {
        Page<ProductInfo> productPage = productFacade.getProducts(brandId, sort, pageable);
        Page<ProductV1Dto.ProductResponse> responsePage = productPage.map(ProductV1Dto.ProductResponse::from);
        return ApiResponse.success(PageResponse.from(responsePage));
    }
}
