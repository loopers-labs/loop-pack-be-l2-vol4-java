package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand Admin V1 API", description = "Loopers 브랜드 관리자 도메인 API 입니다.")
public interface BrandAdminV1ApiSpec {

    @Operation(
        summary = "브랜드 등록",
        description = "관리자가 새로운 브랜드를 등록하고 생성된 브랜드 식별자를 반환한다."
    )
    ApiResponse<BrandAdminV1Dto.CreateResponse> createBrand(BrandAdminV1Dto.CreateRequest request);

    @Operation(
        summary = "브랜드 수정",
        description = "관리자가 브랜드의 이름·설명을 수정하고 식별자를 반환한다."
    )
    ApiResponse<BrandAdminV1Dto.UpdateResponse> updateBrand(Long brandId, BrandAdminV1Dto.UpdateRequest request);

    @Operation(
        summary = "브랜드 상세 조회",
        description = "관리자가 특정 브랜드의 상세 정보(등록·갱신 시각 포함)를 조회한다."
    )
    ApiResponse<BrandAdminV1Dto.DetailResponse> readBrand(Long brandId);

    @Operation(
        summary = "브랜드 목록 조회",
        description = "관리자가 삭제되지 않은 브랜드를 등록 시각 내림차순으로 페이징 조회한다."
    )
    ApiResponse<BrandAdminV1Dto.PageResponse> readBrands(int page, int size);
}
