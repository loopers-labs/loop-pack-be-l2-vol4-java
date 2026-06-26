package com.loopers.product.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.product.domain.ProductSortOption;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Product V1 API", description = "Loopers 상품 조회 API 입니다.")
public interface ProductV1ApiSpec {

    @Operation(
        summary = "상품 단건 조회",
        description = "productId 로 판매중인 상품 정보를 조회합니다. 판매중지·삭제 상품은 조회되지 않습니다."
    )
    ApiResponse<ProductV1Response.Detail> get(Long productId);

    @Operation(
        summary = "상품 목록 조회",
        description = "판매중인 상품 목록을 sort 기준으로 정렬해 반환합니다. (LATEST | PRICE_ASC | LIKES_DESC)"
    )
    ApiResponse<List<ProductV1Response.Detail>> getAll(ProductSortOption sort);
}
