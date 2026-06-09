package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@Tag(name = "Product V1 API", description = "상품 고객 API (활성 상품만)")
public interface ProductV1ApiSpec {

    @Operation(summary = "상품 단건 조회", description = "고객용 — 활성 상품만, 재고 수량 비노출")
    ApiResponse<ProductV1Dto.ProductResponse> getActive(UUID id);

    @Operation(summary = "상품 목록 조회", description = "고객용 — 활성 상품만, brandId 필터 + sort(latest/price_asc/likes_desc) 지원")
    ApiResponse<PageResponse<ProductV1Dto.ProductResponse>> getActiveList(UUID brandId, String sort, Pageable pageable);
}
