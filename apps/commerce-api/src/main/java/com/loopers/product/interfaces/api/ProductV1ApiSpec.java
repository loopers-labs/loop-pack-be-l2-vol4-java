package com.loopers.product.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.product.application.ProductSortOption;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;

@Tag(name = "Product V1 API", description = "Loopers 상품 API 입니다.")
public interface ProductV1ApiSpec {

    @Operation(
        summary = "상품 등록",
        description = "브랜드, 이름, 설명, 가격, 초기 재고로 새 상품을 등록합니다."
    )
    ApiResponse<ProductV1Dto.ProductResponse> create(@Valid ProductV1Dto.CreateRequest request);

    @Operation(
        summary = "상품 단건 조회",
        description = "productId 로 상품 정보를 조회합니다."
    )
    ApiResponse<ProductV1Dto.ProductResponse> get(Long productId);

    @Operation(
        summary = "상품 목록 조회",
        description = "활성 상품 목록을 sort 기준으로 정렬해 반환합니다. (LATEST | PRICE_ASC)"
    )
    ApiResponse<List<ProductV1Dto.ProductResponse>> getAll(ProductSortOption sort);

    @Operation(
        summary = "상품 수정",
        description = "productId 의 이름, 설명, 가격을 변경합니다. 브랜드는 변경되지 않습니다."
    )
    ApiResponse<ProductV1Dto.ProductResponse> update(Long productId, @Valid ProductV1Dto.UpdateRequest request);

    @Operation(
        summary = "상품 삭제",
        description = "productId 의 상품과 재고를 soft delete 합니다."
    )
    ApiResponse<Void> delete(Long productId);
}
