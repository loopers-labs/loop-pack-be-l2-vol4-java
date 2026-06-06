package com.loopers.interfaces.api.product;

import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Product API", description = "상품 조회 API")
public interface ProductV1ApiSpec {

    @Operation(
        summary = "상품 단건 조회",
        description = "활성 상품의 상세 정보를 반환합니다. 재고 수량은 노출하지 않고 구매 가능 여부(purchasable)만 제공합니다. " +
            "삭제되었거나 존재하지 않는 상품이면 404 PRODUCT_NOT_FOUND 를 반환합니다."
    )
    ApiResponse<ProductV1Dto.ProductResponse> getProduct(Long productId);

    @Operation(
        summary = "상품 목록 조회",
        description = "활성 상품 목록을 정렬·페이징하여 반환합니다. brandId 를 지정하면 해당 브랜드 상품만 조회하며, " +
            "미지정 시 전체를 반환합니다. 존재하지 않는 브랜드로 필터하면 빈 목록을 반환합니다. " +
            "sort 는 LATEST(기본)·PRICE_ASC·LIKES_DESC 를 지원합니다."
    )
    ApiResponse<List<ProductV1Dto.ProductResponse>> getAllProducts(Long brandId, ProductSortType sort, int page, int size);
}
