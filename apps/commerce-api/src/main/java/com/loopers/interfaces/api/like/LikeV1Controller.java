package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthHeaders;
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
    public ApiResponse<Object> like(
        AuthHeaders auth,
        @PathVariable(value = "productId") Long productId
    ) {
        likeFacade.like(auth.loginId(), productId);
        return ApiResponse.success();
    }

    @DeleteMapping
    @Override
    public ApiResponse<Object> unlike(
        AuthHeaders auth,
        @PathVariable(value = "productId") Long productId
    ) {
        likeFacade.unlike(auth.loginId(), productId);
        return ApiResponse.success();
    }
}
