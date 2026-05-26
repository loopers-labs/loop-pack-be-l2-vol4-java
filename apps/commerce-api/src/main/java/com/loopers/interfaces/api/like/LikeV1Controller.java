package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.user.UserFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 좋아요 — 사용자당 상품 1개(멱등). 사용자 식별은 X-Loopers-LoginId/LoginPw 헤더 인증으로 처리.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products/{productId}/like")
public class LikeV1Controller {

    private final LikeFacade likeFacade;
    private final UserFacade userFacade;

    @PostMapping
    public ApiResponse<LikeV1Dto.LikedResponse> like(
        @PathVariable(value = "productId") Long productId,
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        Long userId = userFacade.authenticate(loginId, loginPw);
        likeFacade.like(userId, productId);
        return ApiResponse.success(new LikeV1Dto.LikedResponse(productId, true));
    }

    @DeleteMapping
    public ApiResponse<LikeV1Dto.LikedResponse> unlike(
        @PathVariable(value = "productId") Long productId,
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        Long userId = userFacade.authenticate(loginId, loginPw);
        likeFacade.unlike(userId, productId);
        return ApiResponse.success(new LikeV1Dto.LikedResponse(productId, false));
    }

    @GetMapping
    public ApiResponse<LikeV1Dto.LikedResponse> isLiked(
        @PathVariable(value = "productId") Long productId,
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        Long userId = userFacade.authenticate(loginId, loginPw);
        boolean liked = likeFacade.isLiked(userId, productId);
        return ApiResponse.success(new LikeV1Dto.LikedResponse(productId, liked));
    }
}
