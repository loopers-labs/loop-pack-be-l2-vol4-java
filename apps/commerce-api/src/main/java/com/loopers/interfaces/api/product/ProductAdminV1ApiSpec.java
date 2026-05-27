package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product Admin V1 API", description = "Loopers 상품 관리자 도메인 API 입니다.")
public interface ProductAdminV1ApiSpec {

    @Operation(
        summary = "상품 목록 조회",
        description = "관리자가 브랜드 필터·페이지로 상품 목록을 조회한다. 각 항목은 정확한 재고 수량과 등록·갱신 시각을 포함한다."
    )
    ApiResponse<ProductAdminV1Dto.PageResponse> readProducts(Long brandId, int page, int size);

    @Operation(
        summary = "상품 상세 조회",
        description = "관리자가 특정 상품의 상세를 조회한다. 정확한 재고 수량과 등록·갱신 시각을 포함하며, 삭제·미존재 상품은 404다."
    )
    ApiResponse<ProductAdminV1Dto.DetailResponse> readProduct(Long productId);

    @Operation(
        summary = "상품 등록",
        description = "관리자가 소속 브랜드에 새로운 상품을 등록하고 생성된 상품 식별자를 반환한다."
    )
    ApiResponse<ProductAdminV1Dto.CreateResponse> createProduct(ProductAdminV1Dto.CreateRequest request);

    @Operation(
        summary = "상품 수정",
        description = "관리자가 상품의 이름·설명·가격·재고를 수정하고 식별자를 반환한다. 소속 브랜드는 수정 대상이 아니다."
    )
    ApiResponse<ProductAdminV1Dto.UpdateResponse> updateProduct(Long productId, ProductAdminV1Dto.UpdateRequest request);

    @Operation(
        summary = "상품 삭제",
        description = "관리자가 상품을 삭제(soft delete)한다. 존재하지 않거나 이미 삭제된 상품도 정상 응답으로 마무리한다(멱등)."
    )
    ApiResponse<Void> deleteProduct(Long productId);
}
