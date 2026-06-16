package com.loopers.like.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.like.application.LikeQueryService;
import com.loopers.like.application.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/likes")
public class LikeV1Controller implements LikeV1ApiSpec {

    private final LikeService likeService;
    private final LikeQueryService likeQueryService;

    @PostMapping("/products/{productId}")
    @Override
    public ApiResponse<Void> register(
        @AuthenticationPrincipal Long userId,
        @PathVariable Long productId
    ) {
        likeService.register(userId, productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/products/{productId}")
    @Override
    public ApiResponse<Void> cancel(
        @AuthenticationPrincipal Long userId,
        @PathVariable Long productId
    ) {
        likeService.cancel(userId, productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/products")
    @Override
    public ApiResponse<List<LikeV1Response.LikedProduct>> getMyLikes(
        @AuthenticationPrincipal Long userId
    ) {
        List<LikeV1Response.LikedProduct> responses = likeQueryService.getMyLikes(userId).stream()
                .map(LikeV1Response.LikedProduct::from)
                .toList();
        return ApiResponse.success(responses);
    }
}
