package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Like V1 API", description = "좋아요 등록·취소·목록 조회 API. X-Loopers-LoginId / X-Loopers-LoginPw 헤더 필요.")
public interface LikeV1ApiSpec {

    @Operation(summary = "좋아요 등록", description = "상품에 좋아요를 등록합니다. 취소 후 재등록 시 Restore 처리됩니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "등록 성공 (응답 본문 없음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 좋아요한 상품",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    void addLike(
            @Parameter(description = "상품 ID", required = true) String productId,
            @Parameter(hidden = true) String userId
    );

    @Operation(summary = "좋아요 취소", description = "상품 좋아요를 취소합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "취소 성공 (응답 본문 없음)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "좋아요 정보 없음",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    void removeLike(
            @Parameter(description = "상품 ID", required = true) String productId,
            @Parameter(hidden = true) String userId
    );

    @Operation(summary = "좋아요 상품 목록 조회", description = "본인의 좋아요 상품 목록을 페이지네이션으로 조회합니다. 타인 조회 시 403을 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "타인 목록 조회 시도",
                    content = @Content(schema = @Schema(hidden = true))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(schema = @Schema(hidden = true)))
    })
    ApiResponse<PageResult<LikeV1Dto.LikeResponse>> getLikedProducts(
            @Parameter(description = "유저 ID (본인만 조회 가능)", required = true) String userId,
            @Parameter(hidden = true) String authUserId,
            @Parameter(description = "페이지 번호 (0-based, 기본값: 0)") int page,
            @Parameter(description = "페이지 크기 (기본값: 20)") int size
    );
}
