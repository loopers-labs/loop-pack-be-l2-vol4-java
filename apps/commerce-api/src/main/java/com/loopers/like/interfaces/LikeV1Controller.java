package com.loopers.like.interfaces;

import com.loopers.like.application.LikeFacade;
import com.loopers.like.application.LikeInfo;
import com.loopers.product.application.ProductInfo;
import com.loopers.support.auth.CurrentUser;
import com.loopers.support.auth.LoginUser;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// [fix] 좋아요 API URL이 요구사항과 달라 수정
//       POST/DELETE /api/v1/likes → /api/v1/products/{productId}/likes
//       GET /api/v1/likes/products → /api/v1/users/{userId}/likes
@RequiredArgsConstructor
@RestController
public class LikeV1Controller {

    private final LikeFacade likeFacade;

    @PostMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<LikeV1Dto.LikeResponse> addLike(
        @CurrentUser LoginUser loginUser,
        @PathVariable Long productId
    ) {
        LikeInfo info = likeFacade.addLike(loginUser.id(), productId);
        return ApiResponse.success(LikeV1Dto.LikeResponse.from(info));
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Void> cancelLike(
        @CurrentUser LoginUser loginUser,
        @PathVariable Long productId
    ) {
        likeFacade.cancelLike(loginUser.id(), productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    public ApiResponse<List<ProductInfo>> getLikedProducts(
        @CurrentUser LoginUser loginUser,
        @PathVariable Long userId
    ) {
        if (!loginUser.id().equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 좋아요 목록만 조회할 수 있습니다.");
        }
        List<ProductInfo> products = likeFacade.getLikedProducts(loginUser.id());
        return ApiResponse.success(products);
    }
}
