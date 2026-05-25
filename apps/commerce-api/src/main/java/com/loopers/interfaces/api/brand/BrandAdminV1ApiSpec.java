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
}
