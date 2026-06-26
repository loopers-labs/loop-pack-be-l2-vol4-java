package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product V1 API (Customer)", description = "고객용 상품 조회 API. 인증 불필요.")
public interface ProductV1ApiSpec {

    @Operation(
            summary = "상품 목록 조회",
            description = """
                    상품 목록을 페이지네이션으로 조회합니다.
                    - sort: latest(기본, 최신순) | price_asc | price_desc | like_asc | like_desc
                    - 알 수 없는 sort 값은 400을 반환합니다.
                    - brandId를 지정하면 해당 브랜드 상품만 반환합니다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "알 수 없는 sort 값",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<PageResult<ProductV1Dto.PlpResponse>> getAllProducts(
            @Parameter(description = "브랜드 ID 필터 (선택)") String brandId,
            @Parameter(description = "정렬 기준: latest | price_asc | price_desc | like_asc | like_desc (기본값: latest)") String sort,
            @Parameter(description = "페이지 번호 (0-based, 기본값: 0)") int page,
            @Parameter(description = "페이지 크기 (기본값: 20)") int size
    );

    @Operation(summary = "상품 단건 조회", description = "productId로 상품 상세 정보(재고·설명 포함)를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상품 없음",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<ProductV1Dto.PdpResponse> getProduct(
            @Parameter(description = "상품 ID", required = true) String productId
    );
}
