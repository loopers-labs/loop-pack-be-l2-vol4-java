package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand API", description = "브랜드 API")
public interface BrandV1ApiSpec {

    @Operation(
        summary = "브랜드 단건 조회 (고객)",
        description = "활성(미삭제) 브랜드의 정보를 조회합니다. 존재하지 않거나 삭제된 브랜드는 404 를 반환합니다."
    )
    ApiResponse<BrandV1Dto.CustomerResponse> getBrand(Long brandId);
}
