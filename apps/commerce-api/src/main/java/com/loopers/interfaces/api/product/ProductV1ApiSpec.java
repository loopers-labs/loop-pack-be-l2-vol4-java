package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Product V1 API", description = "상품 도메인 API 입니다.")
public interface ProductV1ApiSpec {

    @Operation(summary = "상품 등록", description = "새 상품을 등록합니다.")
    ApiResponse<ProductV1Dto.ProductResponse> createProduct(ProductV1Dto.CreateProductRequest request);

    @Operation(summary = "상품 상세 조회", description = "상품 ID로 브랜드 정보와 좋아요 수를 포함한 상세를 조회합니다.")
    ApiResponse<ProductV1Dto.ProductDetailResponse> getProduct(Long productId);

    @Operation(summary = "상품 목록 조회", description = "브랜드 필터·정렬(latest/price_asc/likes_desc)·페이징으로 상품 목록을 조회합니다.")
    ApiResponse<List<ProductV1Dto.ProductResponse>> getProducts(Long brandId, String sort, int page, int size);

    @Operation(summary = "상품 목록 키셋 커서 조회", description = "좋아요순(likes_desc) 키셋 커서 페이지네이션. cursor 가 없으면 첫 페이지, 응답의 nextCursor 로 다음 페이지를 요청합니다.")
    ApiResponse<ProductV1Dto.CursorPageResponse> getProductsByCursor(Long brandId, String cursor, int size);

    @Operation(summary = "상품 수정", description = "상품 정보를 수정합니다. (브랜드는 변경할 수 없습니다)")
    ApiResponse<ProductV1Dto.ProductResponse> updateProduct(Long productId, ProductV1Dto.UpdateProductRequest request);

    @Operation(summary = "상품 삭제", description = "상품을 삭제합니다. (soft delete)")
    ApiResponse<Void> deleteProduct(Long productId);
}
