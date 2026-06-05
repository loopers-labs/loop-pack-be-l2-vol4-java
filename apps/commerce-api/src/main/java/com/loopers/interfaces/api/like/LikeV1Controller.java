package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeCommand;
import com.loopers.application.like.LikeFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.LoginUser;
import com.loopers.interfaces.api.product.ProductV1Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class LikeV1Controller {

    private final LikeFacade likeFacade;

    @PostMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Void> like(
        @PathVariable Long productId,
        @LoginUser User user
    ) {
        likeFacade.like(new LikeCommand.Like(user.getId(), productId));
        return ApiResponse.success(null);
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Void> unlike(
        @PathVariable Long productId,
        @LoginUser User user
    ) {
        likeFacade.unlike(new LikeCommand.Unlike(user.getId(), productId));
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    public ApiResponse<List<ProductV1Dto.ProductResponse>> getLikedProducts(
        @PathVariable Long userId,
        @LoginUser User user
    ) {
        List<ProductInfo> likedProducts = likeFacade.getLikedProducts(
            new LikeCommand.GetLiked(user.getId(), userId)
        );
        return ApiResponse.success(likedProducts.stream()
            .map(ProductV1Dto.ProductResponse::from)
            .toList());
    }
}
