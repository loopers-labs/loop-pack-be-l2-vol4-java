package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product Admin V1 API", description = "Loopers 상품 관리자 도메인 API 입니다.")
public interface ProductAdminV1ApiSpec {

    @Operation(
        summary = "상품 등록",
        description = "관리자가 소속 브랜드에 새로운 상품을 등록하고 생성된 상품 식별자를 반환한다."
    )
    ApiResponse<ProductAdminV1Dto.CreateResponse> createProduct(ProductAdminV1Dto.CreateRequest request);
}
