package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

@Tag(name = "Brand V1 API", description = "브랜드 고객 API (활성 브랜드만)")
public interface BrandV1ApiSpec {

    @Operation(summary = "브랜드 단건 조회", description = "고객용 — 활성(삭제되지 않은) 브랜드만 반환합니다.")
    ApiResponse<BrandV1Dto.BrandResponse> getActive(UUID id);
}
