package com.loopers.interfaces.api.product;

import com.loopers.domain.product.enums.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;


@Tag(name = "Product V1 API", description = "상품 API")
public interface ProductV1ApiSpec {

    @Operation(summary = "상품 목록 조회", description = "브랜드 ID, 다중 정렬 조건(LATEST/PRICE_ASC/LIKES_DESC), 페이지 정보로 상품 목록을 조회합니다.")
    ApiResponse<PageResponse<ProductV1Dto.ProductResponse>> getProducts(Long brandId, ProductSortType sort, Pageable pageable);

    @Operation(summary = "상품 정보 조회", description = "상품 ID로 상품 정보를 조회합니다.")
    ApiResponse<ProductV1Dto.ProductDetailResponse> getProduct(Long productId);
}