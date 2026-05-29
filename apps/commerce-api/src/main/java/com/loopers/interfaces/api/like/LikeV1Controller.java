package com.loopers.interfaces.api.like;

import com.loopers.application.user.UserInfo;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.LoginUser;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class LikeV1Controller {

    private final LikeService likeService;
    private final ProductService productService;

    @PostMapping("/products/{productId}/likes")
    public ApiResponse<Object> like(
        @LoginUser UserInfo loginUser,
        @PathVariable(value = "productId") Long productId
    ) {
        productService.getProduct(productId); // 상품 존재 확인 (없으면 NOT_FOUND)
        likeService.like(loginUser.id(), productId);
        return ApiResponse.success();
    }

    @DeleteMapping("/products/{productId}/likes")
    public ApiResponse<Object> unlike(
        @LoginUser UserInfo loginUser,
        @PathVariable(value = "productId") Long productId
    ) {
        productService.getProduct(productId); // 상품 존재 확인 (없으면 NOT_FOUND)
        likeService.unlike(loginUser.id(), productId);
        return ApiResponse.success();
    }

    @GetMapping("/users/{userId}/likes")
    public ApiResponse<List<LikeV1Dto.LikedProductResponse>> getLikedProducts(
        @LoginUser UserInfo loginUser,
        @PathVariable(value = "userId") Long userId
    ) {
        if (!userId.equals(loginUser.id())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "본인의 좋아요 목록만 조회할 수 있습니다.");
        }
        List<LikeV1Dto.LikedProductResponse> responses =
            productService.getProductsByIds(likeService.getLikedProductIds(userId)).stream()
                .map(LikeV1Dto.LikedProductResponse::from)
                .toList();
        return ApiResponse.success(responses);
    }
}
