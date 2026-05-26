package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import com.loopers.interfaces.api.auth.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Like V1 API", description = "Loopers 좋아요 도메인 API 입니다.")
public interface LikeV1ApiSpec {

    @Operation(
        summary = "좋아요 등록",
        description = "회원이 특정 상품에 좋아요를 등록한다. 이미 좋아요한 상품이면 별도 동작 없이 정상 응답한다(멱등)."
    )
    ApiResponse<Void> createLike(Long productId, @Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser);

    @Operation(
        summary = "좋아요 취소",
        description = "회원이 특정 상품의 좋아요를 취소한다. 좋아요 기록이 없어도 정상 응답한다(멱등). 삭제된 상품은 404."
    )
    ApiResponse<Void> deleteLike(Long productId, @Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser);
}
