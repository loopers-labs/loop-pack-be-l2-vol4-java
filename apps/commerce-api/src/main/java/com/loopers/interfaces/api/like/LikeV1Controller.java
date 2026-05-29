package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.common.interceptor.AuthInterceptor;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import com.loopers.interfaces.api.product.ProductV1Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class LikeV1Controller implements LikeV1ApiSpec {

    private final LikeFacade likeFacade;

    @PostMapping("/products/{productId}/likes")
    @Override
    public ApiResponse<LikeV1Dto.LikeResponse> like(
        @PathVariable UUID productId,
        @RequestAttribute(AuthInterceptor.AUTHENTICATED_USER) UserModel user
    ) {
        return ApiResponse.success(LikeV1Dto.LikeResponse.from(likeFacade.like(productId, user)));
    }

    @DeleteMapping("/products/{productId}/likes")
    @Override
    public ApiResponse<LikeV1Dto.LikeResponse> unlike(
        @PathVariable UUID productId,
        @RequestAttribute(AuthInterceptor.AUTHENTICATED_USER) UserModel user
    ) {
        return ApiResponse.success(LikeV1Dto.LikeResponse.from(likeFacade.unlike(productId, user)));
    }

    @GetMapping("/users/{userId}/likes")
    @Override
    public ApiResponse<PageResponse<ProductV1Dto.ProductResponse>> getLikeList(
        @PathVariable UUID userId,
        @RequestAttribute(AuthInterceptor.AUTHENTICATED_USER) UserModel user,
        Pageable pageable
    ) {
        return ApiResponse.success(
            PageResponse.from(
                likeFacade.getLikeList(userId, user, pageable)
                    .map(ProductV1Dto.ProductResponse::from)
            )
        );
    }
}
