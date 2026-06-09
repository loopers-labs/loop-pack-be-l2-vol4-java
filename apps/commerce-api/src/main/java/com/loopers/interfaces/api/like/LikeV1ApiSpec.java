package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;

@Tag(name = "Like V1 API", description = "Loopers 상품 좋아요 API 입니다.")
public interface LikeV1ApiSpec {

    @Operation(summary = "좋아요 등록", description = "상품에 좋아요를 등록합니다. (멱등)")
    ApiResponse<Void> like(AuthUser authUser, @Positive Long productId);

    @Operation(summary = "좋아요 취소", description = "상품 좋아요를 취소합니다. (멱등)")
    ApiResponse<Void> unlike(AuthUser authUser, @Positive Long productId);
}
