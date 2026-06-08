package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product V1 API", description = "Loopers 상품 API 입니다.")
public interface ProductV1ApiSpec {

    @Operation(
        summary = "상품 조회",
        description = "상품 ID 로 상품 정보를 조회합니다."
    )
    ApiResponse<ProductV1Dto.ProductResponse> getProduct(
        @Parameter(name = "productId", in = ParameterIn.PATH, required = true, description = "상품 ID")
        Long productId
    );

    @Operation(
        summary = "상품 목록 조회",
        description = "브랜드 필터·정렬(latest/price_asc/likes_desc)·페이징으로 상품 목록을 조회합니다."
    )
    ApiResponse<ProductV1Dto.ProductPageResponse> getProducts(
        @Parameter(name = "brandId", in = ParameterIn.QUERY, description = "브랜드 ID(필터, 선택)")
        Long brandId,
        @Parameter(name = "sort", in = ParameterIn.QUERY, description = "정렬: latest(기본)/price_asc/likes_desc")
        String sort,
        @Parameter(name = "page", in = ParameterIn.QUERY, description = "페이지(0부터, 기본 0)")
        int page,
        @Parameter(name = "size", in = ParameterIn.QUERY, description = "페이지 크기(기본 20)")
        int size
    );
}
