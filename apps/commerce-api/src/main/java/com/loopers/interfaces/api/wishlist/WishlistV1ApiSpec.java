package com.loopers.interfaces.api.wishlist;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Wishlist V1 API", description = "찜 API")
public interface WishlistV1ApiSpec {

    @Operation(summary = "상품 좋아요 등록", description = "상품을 찜 목록에 추가합니다.")
    ApiResponse<Void> addLike(Long productId, Long userId);

    @Operation(summary = "상품 좋아요 취소", description = "상품을 찜 목록에서 제거합니다.")
    ApiResponse<Void> removeLike(Long productId, Long userId);

    @Operation(summary = "좋아요한 상품 조회", description = "사용자가 찜한 상품 목록을 조회합니다.")
    ApiResponse<List<WishlistV1Dto.LikedProductResponse>> getLikedProducts(Long userId);
}
