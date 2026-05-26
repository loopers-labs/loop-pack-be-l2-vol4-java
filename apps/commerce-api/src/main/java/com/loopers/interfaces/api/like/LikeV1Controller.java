package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.interfaces.api.user.AuthUser;
import com.loopers.interfaces.api.user.AuthUserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class LikeV1Controller {

    private final LikeFacade likeFacade;

    /**
     * 좋아요 등록 - 멱등 동작 (P-1).
     * 이미 좋아요한 상태에서 다시 호출해도 동일하게 200 OK 응답.
     */
    @PostMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Void> like(
        @AuthUser AuthUserContext authUser,
        @PathVariable Long productId
    ) {
        likeFacade.like(authUser.userId(), productId);
        return ApiResponse.success(null);
    }

    /**
     * 좋아요 취소 - 멱등 동작 (P-2).
     * 좋아요가 없어도 200 OK 응답.
     */
    @DeleteMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Void> unlike(
        @AuthUser AuthUserContext authUser,
        @PathVariable Long productId
    ) {
        likeFacade.unlike(authUser.userId(), productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    public ApiResponse<List<ProductV1Dto.ProductResponse>> getLikedProducts(
        @AuthUser AuthUserContext authUser,
        @PathVariable Long userId
    ) {
        List<ProductInfo> infos = likeFacade.getLikedProducts(userId);
        List<ProductV1Dto.ProductResponse> responses = infos.stream()
            .map(ProductV1Dto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
