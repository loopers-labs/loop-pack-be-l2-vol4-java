package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product V1 API (Admin)", description = "어드민용 상품 관리 API. X-Loopers-Ldap: loopers.admin 헤더 필요.")
@SecurityRequirement(name = "X-Loopers-Ldap")
public interface ProductAdminV1ApiSpec {

    @Operation(summary = "상품 등록", description = "새 상품을 등록합니다. brandId로 브랜드 존재 여부를 검증합니다. 재고(Inventory)도 함께 생성됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공, 생성된 상품 ID 반환"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "브랜드 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "어드민 인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<ProductV1Dto.CreateProductResponse> createProduct(ProductV1Dto.CreateProductRequest request);

    @Operation(summary = "상품 목록 조회", description = "상품 목록을 페이지네이션으로 조회합니다. 재고·수정일 포함.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "어드민 인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<PageResult<ProductV1Dto.AdminPlpResponse>> getAllProducts(
            @Parameter(description = "브랜드 ID 필터 (선택)") String brandId,
            @Parameter(description = "페이지 번호 (0-based, 기본값: 0)") int page,
            @Parameter(description = "페이지 크기 (기본값: 20)") int size
    );

    @Operation(summary = "상품 단건 조회", description = "productId로 상품 전체 정보(재고·설명·수정일 포함)를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상품 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "어드민 인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<ProductV1Dto.AdminPdpResponse> getProduct(
            @Parameter(description = "상품 ID", required = true) String productId
    );

    @Operation(summary = "상품 수정", description = "상품 이름·설명·가격·재고 수량을 수정합니다. 브랜드는 변경할 수 없습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "수정 성공 (응답 본문 없음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상품 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "어드민 인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    void updateProduct(
            @Parameter(description = "상품 ID", required = true) String productId,
            ProductV1Dto.UpdateProductRequest request
    );

    @Operation(summary = "상품 삭제", description = "상품을 Soft Delete합니다. 연관된 재고·좋아요도 연쇄 Soft Delete됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공 (응답 본문 없음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "상품 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "어드민 인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    void deleteProduct(
            @Parameter(description = "상품 ID", required = true) String productId
    );
}
