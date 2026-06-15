package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Like V1 API", description = "Loopers 좋아요 대고객 API 입니다.")
public interface LikeV1ApiSpec {

    @Operation(
            summary = "상품 좋아요 등록",
            description = "로그인 사용자가 상품(productId)에 좋아요를 등록합니다. 멱등하게 동작합니다."
    )
    ApiResponse<Void> register(Long userId, Long productId);

    @Operation(
            summary = "상품 좋아요 취소",
            description = "로그인 사용자가 상품(productId)에 등록한 좋아요를 취소합니다. 멱등하게 동작합니다."
    )
    ApiResponse<Void> cancel(Long userId, Long productId);

    @Operation(
            summary = "내 좋아요 목록 조회",
            description = "본인(userId)이 좋아요한 상품을 페이징하여 반환합니다. 본인 외 사용자의 목록은 조회할 수 없습니다."
    )
    ApiResponse<LikeV1Dto.LikePageResponse> getMyLikes(Long loginUserId, Long userId, int page, int size);
}
