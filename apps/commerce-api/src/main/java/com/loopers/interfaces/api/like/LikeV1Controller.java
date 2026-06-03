package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.user.UserFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.ProductV1Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 좋아요 — 사용자당 상품 1개(멱등). 사용자 식별은 X-Loopers-LoginId/LoginPw 헤더 인증으로 처리.
 */
@RequiredArgsConstructor
@RestController
public class LikeV1Controller {

    private final LikeFacade likeFacade;
    private final UserFacade userFacade;

    @PostMapping("/api/v1/products/{productId}/like")
    public ApiResponse<LikeV1Dto.LikedResponse> like(
        @PathVariable(value = "productId") Long productId,
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        Long userId = userFacade.authenticate(loginId, loginPw);
        likeFacade.like(userId, productId);
        return ApiResponse.success(new LikeV1Dto.LikedResponse(productId, true));
    }

    @DeleteMapping("/api/v1/products/{productId}/like")
    public ApiResponse<LikeV1Dto.LikedResponse> unlike(
        @PathVariable(value = "productId") Long productId,
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        Long userId = userFacade.authenticate(loginId, loginPw);
        likeFacade.unlike(userId, productId);
        return ApiResponse.success(new LikeV1Dto.LikedResponse(productId, false));
    }

    @GetMapping("/api/v1/products/{productId}/like")
    public ApiResponse<LikeV1Dto.LikedResponse> isLiked(
        @PathVariable(value = "productId") Long productId,
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        Long userId = userFacade.authenticate(loginId, loginPw);
        boolean liked = likeFacade.isLiked(userId, productId);
        return ApiResponse.success(new LikeV1Dto.LikedResponse(productId, liked));
    }

    /** 내가 좋아요한 상품 목록 — 본인만 조회 가능(URL에 타 사용자 명시 불가, 01 §7.4). */
    @GetMapping("/api/v1/users/me/likes")
    public ApiResponse<List<ProductV1Dto.ProductResponse>> getMyLikedProducts(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        Long userId = userFacade.authenticate(loginId, loginPw);
        List<ProductV1Dto.ProductResponse> responses = likeFacade.getLikedProducts(userId, page, size).stream()
            .map(ProductV1Dto.ProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
