package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product V1 API", description = "Loopers 상품 도메인 API 입니다.")
public interface ProductV1ApiSpec {

    @Operation(
        summary = "상품 목록 조회",
        description = "회원·비회원 누구나 브랜드 필터·정렬(latest·price_asc·likes_desc)·페이지로 상품 목록을 조회한다. 각 항목은 재고 가용 여부와 좋아요 수를 포함한다."
    )
    ApiResponse<ProductV1Dto.PageResponse> readProducts(Long brandId, String sort, int page, int size);

    @Operation(
        summary = "상품 상세 조회",
        description = "회원·비회원 누구나 특정 상품의 상세를 조회한다. 설명·재고 가용 여부·좋아요 수를 포함하며, 삭제·미존재 상품은 404다."
    )
    ApiResponse<ProductV1Dto.DetailResponse> readProduct(Long productId);
}
