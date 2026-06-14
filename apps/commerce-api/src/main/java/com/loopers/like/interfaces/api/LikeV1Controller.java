package com.loopers.like.interfaces.api;

import com.loopers.like.application.LikeFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products/{productId}/likes")
public class LikeV1Controller {

    private final LikeFacade likeFacade;

    @PostMapping
    public ApiResponse<Object> like(
        @AuthenticationPrincipal Long userId,
        @PathVariable Long productId
    ) {
        likeFacade.like(userId, productId);
        return ApiResponse.success();
    }

    @DeleteMapping
    public ApiResponse<Object> unlike(
        @AuthenticationPrincipal Long userId,
        @PathVariable Long productId
    ) {
        likeFacade.unlike(userId, productId);
        return ApiResponse.success();
    }
}
