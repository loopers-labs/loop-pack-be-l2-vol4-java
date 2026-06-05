package com.loopers.like.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Like V1 API", description = "Loopers 좋아요 API 입니다.")
public interface LikeV1ApiSpec {

    @Operation(
        summary = "좋아요 등록",
        description = "인증된 사용자가 productId 의 상품에 좋아요를 등록합니다. 이미 활성 좋아요면 그대로 두고, 취소된 좋아요라면 복구합니다. 멱등."
    )
    ApiResponse<Void> register(@Parameter(hidden = true) Long userId, Long productId);

    @Operation(
        summary = "좋아요 취소",
        description = "인증된 사용자의 productId 좋아요를 취소합니다. 활성 좋아요면 비활성화, 그 외엔 무동작. 멱등."
    )
    ApiResponse<Void> cancel(@Parameter(hidden = true) Long userId, Long productId);

    @Operation(
        summary = "내 좋아요 상품 목록",
        description = "인증된 사용자가 활성 좋아요한 상품 목록을 반환합니다. 삭제된 상품은 제외됩니다."
    )
    ApiResponse<List<LikeV1Response.LikedProduct>> getMyLikes(@Parameter(hidden = true) Long userId);
}
