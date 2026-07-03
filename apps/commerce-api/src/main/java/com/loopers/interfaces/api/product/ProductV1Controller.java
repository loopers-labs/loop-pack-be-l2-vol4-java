package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductCriteria;
import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller {

    private final ProductFacade productFacade;

    @PostMapping
    public ApiResponse<ProductV1Dto.ProductResponse> createProduct(
        @RequestBody ProductV1Dto.CreateProductRequest request
    ) {
        ProductInfo info = productFacade.createProduct(new ProductCriteria.Create(
            request.brandId(),
            request.name(),
            request.description(),
            request.price(),
            request.stock(),
            request.imageUrl()
        ));
        return ApiResponse.success(ProductV1Dto.ProductResponse.from(info));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductV1Dto.ProductDetailResponse> getProduct(
        @PathVariable("productId") Long productId,
        @org.springframework.web.bind.annotation.RequestHeader(
            value = "X-Loopers-User-Id", required = false) Long userId
    ) {
        ProductDetailInfo detail = productFacade.getProductDetail(productId, userId);
        return ApiResponse.success(ProductV1Dto.ProductDetailResponse.from(detail));
    }

    @GetMapping
    public ApiResponse<List<ProductV1Dto.ProductResponse>> listProducts(
        @RequestParam(value = "sort", required = false) ProductSortType sortType,
        @RequestParam(value = "brandId", required = false) Long brandId
    ) {
        List<ProductInfo> infos = productFacade.listProducts(new ProductCriteria.List(sortType, brandId));
        List<ProductV1Dto.ProductResponse> responses = infos.stream()
            .map(ProductV1Dto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
