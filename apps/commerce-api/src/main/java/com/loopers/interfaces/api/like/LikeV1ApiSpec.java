package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Like API", description = "상품 좋아요 API")
public interface LikeV1ApiSpec {

    @Operation(summary = "좋아요 등록", description = "로그인한 회원이 특정 상품에 좋아요를 등록합니다. 같은 상품에 두 번 호출해도 멱등합니다.")
    ApiResponse<Void> like(LoginUser loginUser, Long productId);

    @Operation(summary = "좋아요 취소", description = "로그인한 회원이 특정 상품의 좋아요를 취소합니다. 좋아요가 없는 상태에서 호출해도 멱등합니다.")
    ApiResponse<Void> unlike(LoginUser loginUser, Long productId);

    @Operation(
        summary = "내 좋아요 목록 조회",
        description = "로그인한 회원이 자신이 좋아요한 active 상품 목록을 좋아요 누른 시각 최신순으로 조회한다. soft-deleted 된 상품은 제외된다."
    )
    ApiResponse<List<LikeV1Dto.LikedProductResponse>> getMyLikes(LoginUser loginUser, int page, int size);
}
