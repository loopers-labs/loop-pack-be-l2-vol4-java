package com.loopers.interfaces.api.like;

import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import com.loopers.interfaces.api.product.ProductV1Dto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

@Tag(name = "좋아요 API")
public interface LikeV1ApiSpec {

    @Operation(summary = "좋아요 등록")
    ApiResponse<LikeV1Dto.LikeResponse> like(UUID productId, UserModel user);

    @Operation(summary = "좋아요 취소")
    ApiResponse<LikeV1Dto.LikeResponse> unlike(UUID productId, UserModel user);

    @Operation(summary = "좋아요 목록 조회")
    ApiResponse<PageResponse<ProductV1Dto.ProductResponse>> getLikeList(UUID userId, UserModel user, Pageable pageable);
}
