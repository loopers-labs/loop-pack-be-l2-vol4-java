package com.loopers.brand.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Brand V1 API", description = "Loopers 브랜드 조회 API 입니다.")
public interface BrandV1ApiSpec {

    @Operation(
        summary = "브랜드 단건 조회",
        description = "brandId 로 브랜드 정보를 조회합니다."
    )
    ApiResponse<BrandV1Response.Detail> get(Long brandId);

    @Operation(
        summary = "브랜드 목록 조회",
        description = "활성 상태인 브랜드 목록을 반환합니다."
    )
    ApiResponse<List<BrandV1Response.Detail>> getAll();
}
