package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Like V1 API", description = "상품 좋아요 API 입니다.")
public interface LikeV1ApiSpec {

    @Operation(summary = "좋아요 등록", description = "헤더로 식별한 유저가 상품에 좋아요를 등록합니다. (멱등)")
    ApiResponse<Object> like(AuthHeaders auth, Long productId);

    @Operation(summary = "좋아요 취소", description = "헤더로 식별한 유저가 상품 좋아요를 취소합니다. (멱등)")
    ApiResponse<Object> unlike(AuthHeaders auth, Long productId);
}
