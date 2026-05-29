package com.loopers.interfaces.api.product.admin;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product Admin API", description = "어드민 상품 API")
public interface ProductAdminV1ApiSpec {

    @Operation(
        summary = "어드민 상품 목록 조회",
        description = "삭제된 상품을 포함한 전체 상품을 최신순으로 페이지 단위 조회합니다. " +
            "brandId 를 지정하면 해당 브랜드 상품만 조회합니다. 기본 page=0, size=20. " +
            "각 항목에는 재고 수량(stockQuantity)과 deletedAt 이 포함됩니다."
    )
    ApiResponse<ProductAdminV1Dto.PageResponse> getProducts(Long brandId, int page, int size);

    @Operation(
        summary = "어드민 상품 단건 조회",
        description = "삭제된 상품도 조회할 수 있습니다. 응답에는 재고 수량과 등록·수정·삭제 일시가 포함됩니다. " +
            "존재하지 않는 상품이면 404 PRODUCT_NOT_FOUND 를 반환합니다."
    )
    ApiResponse<ProductAdminV1Dto.AdminProductDetail> getProduct(Long productId);
}
