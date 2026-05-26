package com.loopers.interfaces.api.like;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.like.LikeFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import com.loopers.interfaces.api.auth.LoginUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products/{productId}/likes")
public class LikeV1Controller implements LikeV1ApiSpec {

    private final LikeFacade likeFacade;

    @Override
    @PostMapping
    public ApiResponse<Void> createLike(
        @PathVariable Long productId,
        @LoginUser AuthenticatedUser loginUser
    ) {
        likeFacade.createLike(loginUser.userId(), productId);

        return ApiResponse.success();
    }

    @Override
    @DeleteMapping
    public ApiResponse<Void> deleteLike(
        @PathVariable Long productId,
        @LoginUser AuthenticatedUser loginUser
    ) {
        likeFacade.deleteLike(loginUser.userId(), productId);

        return ApiResponse.success();
    }
}
