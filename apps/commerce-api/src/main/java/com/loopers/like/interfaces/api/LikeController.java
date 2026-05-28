package com.loopers.like.interfaces.api;

import com.loopers.common.interfaces.api.ApiResponse;
import com.loopers.like.application.LikeFacade;
import com.loopers.member.application.MemberFacade;
import com.loopers.product.application.ProductDetailInfo;
import com.loopers.product.interfaces.api.ProductDetailResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class LikeController {

    private final LikeFacade likeFacade;
    private final MemberFacade memberFacade;

    @PostMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Void> registerLike(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @PathVariable("productId") Long productId) {
        Long memberId = memberFacade.authenticate(loginId, loginPw);
        likeFacade.registerLike(memberId, productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Void> cancelLike(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @PathVariable("productId") Long productId) {
        Long memberId = memberFacade.authenticate(loginId, loginPw);
        likeFacade.cancelLike(memberId, productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    public ApiResponse<List<ProductDetailResponse>> getMyLikes(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @PathVariable("userId") Long userId) {
        Long memberId = memberFacade.authenticate(loginId, loginPw);
        if (!memberId.equals(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "타 유저의 좋아요 목록은 조회할 수 없습니다.");
        }
        List<ProductDetailInfo> infos = likeFacade.getMyLikedProducts(memberId);
        return ApiResponse.success(
            infos.stream().map(ProductDetailResponse::from).toList());
    }
}
