package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;

@Tag(name = "Brand V1 API (Admin)", description = "어드민용 브랜드 관리 API. X-Loopers-Ldap: loopers.admin 헤더 필요.")
@SecurityRequirement(name = "X-Loopers-Ldap")
public interface BrandAdminV1ApiSpec {

    @Operation(summary = "브랜드 목록 조회", description = "등록된 브랜드를 페이지네이션으로 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "어드민 인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<PageResult<BrandV1Dto.BrandAdminResponse>> getBrands(Pageable pageable);

    @Operation(summary = "브랜드 단건 조회", description = "brandId로 브랜드 상세 정보를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "브랜드 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "어드민 인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<BrandV1Dto.BrandAdminResponse> getBrand(
            @Parameter(description = "브랜드 ID", required = true) String brandId
    );

    @Operation(summary = "브랜드 등록", description = "새 브랜드를 등록합니다. 브랜드명은 중복 불가입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "브랜드명 중복",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "어드민 인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<BrandV1Dto.BrandAdminResponse> createBrand(BrandV1Dto.CreateBrandRequest request);

    @Operation(summary = "브랜드 수정", description = "브랜드 이름과 설명을 수정합니다. 브랜드명은 중복 불가입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "브랜드 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "브랜드명 중복",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "어드민 인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<BrandV1Dto.BrandAdminResponse> updateBrand(
            @Parameter(description = "브랜드 ID", required = true) String brandId,
            BrandV1Dto.UpdateBrandRequest request
    );

    @Operation(summary = "브랜드 삭제", description = "브랜드를 Soft Delete합니다. 연관된 상품·재고·좋아요도 연쇄 Soft Delete됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "브랜드 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "어드민 인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<Object> deleteBrand(
            @Parameter(description = "브랜드 ID", required = true) String brandId
    );
}
