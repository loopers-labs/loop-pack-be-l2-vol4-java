package com.loopers.interfaces.api.product;

import com.loopers.domain.product.ProductSortOption;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product V1 API", description = "상품 관련 API")
public interface ProductV1ApiSpec {

    @Operation(summary = "상품 목록 조회",
            description = "brandId 필터(선택), 정렬(latest/price_asc/likes_desc), 페이징 지원.")
    ApiResponse<ProductV1Dto.ProductListResponse> getProducts(Long brandId, ProductSortOption sort, Integer page, Integer size);

    @Operation(summary = "상품 상세 조회", description = "productId 로 상품 + 브랜드 조합 정보를 반환.")
    ApiResponse<ProductV1Dto.ProductDetailResponse> getProduct(Long productId);
}
