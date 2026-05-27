package com.loopers.interfaces.api.catalog.product;

import com.loopers.application.catalog.product.ProductQuery;
import com.loopers.application.catalog.product.ProductQueryService;
import com.loopers.application.catalog.product.ProductResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.support.HeaderValidator;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductQueryService productQueryService;

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductDetailResponse> getProduct(
        @PathVariable(value = "productId") Long productId,
        @RequestHeader(value = HeaderValidator.LOGIN_ID, required = false) String loginId
    ) {
        ProductResult result = productQueryService.getOnSaleProduct(productId, loginId);
        return ApiResponse.success(ProductV1Dto.ProductDetailResponse.from(result));
    }

    @GetMapping
    public ApiResponse<PageResponse<ProductV1Dto.ProductListItemResponse>> searchProducts(
        @RequestParam(required = false) Long brandId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "latest") String sort,
        @RequestHeader(value = HeaderValidator.LOGIN_ID, required = false) String loginId
    ) {
        PageResult<ProductResult> result = productQueryService.searchOnSaleProducts(
            new ProductQuery.Search(brandId, page, size, sort, loginId)
        );

        return ApiResponse.success(PageResponse.from(result, ProductV1Dto.ProductListItemResponse::from));
    }
}
