package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.dto.ProductV1Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Tag(name = "Product V1 API", description = "Loopers 상품 API 입니다.")
public interface ProductV1ApiSpec {

    @Operation(summary = "상품 목록 조회", description = "브랜드/정렬 조건으로 상품을 페이징하여 조회합니다.")
    ApiResponse<Page<ProductV1Response>> search(Long brandId, String sort, Pageable pageable);

    @Operation(summary = "상품 상세 조회", description = "상품 단건을 조회합니다.")
    ApiResponse<ProductV1Response> getProduct(Long productId);
}
