package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand V1 API (Customer)", description = "고객용 브랜드 조회 API")
public interface BrandV1ApiSpec {

    @Operation(summary = "브랜드 단건 조회", description = "brandId로 브랜드 정보를 조회합니다. 인증 불필요.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "브랜드 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<BrandV1Dto.BrandResponse> getBrand(
            @Parameter(description = "브랜드 ID", required = true) String brandId
    );
}
