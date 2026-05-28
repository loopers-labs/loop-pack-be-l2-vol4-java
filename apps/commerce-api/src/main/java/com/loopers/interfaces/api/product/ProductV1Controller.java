package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductSummaryInfo;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductFacade productFacade;

    @GetMapping
    public ApiResponse<Page<ProductV1Dto.ProductSummaryResponse>> getProducts(
            @RequestParam(value = "brandId", required = false) Long brandId,
            @RequestParam(value = "sort", required = false) ProductSortType sort,
            @PageableDefault(size = 20) final Pageable pageable
    ) {
        Page<ProductSummaryInfo> infos = productFacade.getProducts(brandId, sort, pageable);
        Page<ProductV1Dto.ProductSummaryResponse> response = infos.map(ProductV1Dto.ProductSummaryResponse::from);
        return ApiResponse.success(response);
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductDetailResponse> getProductDetail(
            @PathVariable(value = "productId") Long productId
    ) {
        ProductDetailInfo info = productFacade.getProductDetail(productId);
        ProductV1Dto.ProductDetailResponse response = ProductV1Dto.ProductDetailResponse.from(info);
        return ApiResponse.success(response);
    }
}
