package com.loopers.interfaces.api.like;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.product.ProductSummaryInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.interfaces.api.product.ProductV1Dto;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/{userId}/likes")
public class UserLikeV1Controller implements UserLikeV1ApiSpec {

    private final LikeFacade likeFacade;

    @Override
    @GetMapping
    public ApiResponse<ProductV1Dto.PageResponse> readLikedProducts(
        @PathVariable Long userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @LoginUser AuthenticatedUser loginUser
    ) {
        Page<ProductSummaryInfo> likedProductsInfo = likeFacade.readLikedProducts(loginUser.userId(), userId, page, size);

        return ApiResponse.success(ProductV1Dto.PageResponse.from(likedProductsInfo));
    }
}
