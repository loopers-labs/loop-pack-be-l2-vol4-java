package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.ProductDto;
import com.loopers.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class LikeController {

    private final LikeFacade likeFacade;

    /** POST /api/products/{productId}/likes — 좋아요 등록 */
    @PostMapping("/api/products/{productId}/likes")
    public ApiResponse<Void> addLike(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long productId
    ) {
        likeFacade.addLike(principal.getId(), productId);
        return ApiResponse.success(null);
    }

    /** DELETE /api/products/{productId}/likes — 좋아요 취소 */
    @DeleteMapping("/api/products/{productId}/likes")
    public ApiResponse<Void> removeLike(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long productId
    ) {
        likeFacade.removeLike(principal.getId(), productId);
        return ApiResponse.success(null);
    }

    /** GET /api/users/{userId}/likes — 좋아요한 상품 목록 */
    @GetMapping("/api/users/{userId}/likes")
    public ApiResponse<List<ProductDto.ProductResponse>> getLikedProducts(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long userId
    ) {
        if (!principal.getId().equals(userId)) {
            throw new com.loopers.support.error.CoreException(
                com.loopers.support.error.ErrorType.BAD_REQUEST,
                "본인의 좋아요 목록만 조회할 수 있습니다."
            );
        }
        List<ProductInfo> products = likeFacade.getLikedProducts(userId);
        return ApiResponse.success(products.stream().map(ProductDto.ProductResponse::from).toList());
    }
}
