package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product V1 API", description = "Loopers 상품 대고객 API 입니다.")
public interface ProductV1ApiSpec {

    @Operation(
            summary = "상품 목록 조회",
            description = "브랜드(brandId)·정렬(sort)·페이지 조건으로 상품을 페이징하여 반환합니다."
    )
    ApiResponse<ProductV1Dto.PageResponse> getProducts(Long brandId, String sort, int page, int size);

    @Operation(
            summary = "상품 단건 조회",
            description = "상품 식별자(productId)로 상품 상세를 반환합니다."
    )
    ApiResponse<ProductV1Dto.DetailResponse> getProduct(Long productId);
}
