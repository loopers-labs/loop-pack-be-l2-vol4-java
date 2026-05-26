package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products/{productId}/likes")
public class LikeV1Controller implements LikeV1ApiSpec {

    private final LikeFacade likeFacade;

    @PostMapping
    @Override
    public ApiResponse<Void> like(
        @AuthenticatedUser LoginUser loginUser,
        @PathVariable("productId") Long productId
    ) {
        likeFacade.like(loginUser.id(), productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping
    @Override
    public ApiResponse<Void> unlike(
        @AuthenticatedUser LoginUser loginUser,
        @PathVariable("productId") Long productId
    ) {
        likeFacade.unlike(loginUser.id(), productId);
        return ApiResponse.success(null);
    }
}
