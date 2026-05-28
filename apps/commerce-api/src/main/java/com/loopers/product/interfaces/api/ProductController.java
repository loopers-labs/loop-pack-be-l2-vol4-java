package com.loopers.product.interfaces.api;

import com.loopers.common.interfaces.api.ApiResponse;
import com.loopers.common.interfaces.api.PagedResponse;
import com.loopers.product.application.ProductDetailInfo;
import com.loopers.product.application.ProductFacade;
import com.loopers.product.domain.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductFacade productFacade;

    @GetMapping
    public ApiResponse<PagedResponse<ProductDetailResponse>> getProducts(
        @RequestParam(value = "brandId", required = false) Long brandId,
        @RequestParam(value = "sort", required = false) String sort,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<ProductDetailInfo> result =
            productFacade.getProducts(brandId, ProductSortType.from(sort), page, size);
        return ApiResponse.success(
            PagedResponse.from(result.map(ProductDetailResponse::from)));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> getProduct(
        @PathVariable("productId") Long productId) {
        ProductDetailInfo info = productFacade.getProductDetail(productId);
        return ApiResponse.success(ProductDetailResponse.from(info));
    }
}
