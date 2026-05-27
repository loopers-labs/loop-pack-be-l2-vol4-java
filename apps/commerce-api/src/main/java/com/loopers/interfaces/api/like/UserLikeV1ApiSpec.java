package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.interfaces.api.product.ProductV1Dto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User Like V1 API", description = "Loopers 회원 좋아요 도메인 API 입니다.")
public interface UserLikeV1ApiSpec {

    @Operation(
        summary = "좋아요한 상품 목록 조회",
        description = "회원이 자신이 좋아요한 상품을 최신 좋아요 순으로 조회한다. 경로 회원 식별자가 인증 회원과 다르거나 미존재면 빈 목록을 반환한다."
    )
    ApiResponse<ProductV1Dto.PageResponse> readLikedProducts(
        Long userId,
        int page,
        int size,
        @Parameter(hidden = true) @LoginUser AuthenticatedUser loginUser
    );
}
