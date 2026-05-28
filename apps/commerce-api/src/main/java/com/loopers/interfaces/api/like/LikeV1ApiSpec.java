package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Like V1 API", description = "상품 좋아요 관련 API")
public interface LikeV1ApiSpec {

    @Operation(summary = "좋아요 등록", description = "상품에 좋아요를 등록합니다. 멱등 동작.")
    ApiResponse<Object> like(Long userId, Long productId);

    @Operation(summary = "좋아요 취소", description = "상품 좋아요를 취소합니다. 멱등 동작.")
    ApiResponse<Object> unlike(Long userId, Long productId);
}
