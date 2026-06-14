package com.loopers.like.interfaces.api;

import com.loopers.like.application.GetMyLikesCommand;
import com.loopers.like.application.LikeFacade;
import com.loopers.product.application.ProductListInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.product.interfaces.api.ProductV1Dto;
import com.loopers.support.pagination.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users/{userId}/likes")
public class UserLikeV1Controller {

    private final LikeFacade likeFacade;

    @GetMapping
    public ApiResponse<PageResponse<ProductV1Dto.ProductResponse>> getMyLikes(
        @AuthenticationPrincipal Long authenticatedUserId,
        @PathVariable Long userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PageResult<ProductListInfo> products = likeFacade.getMyLikes(
            new GetMyLikesCommand(userId, authenticatedUserId, page, size)
        );
        return ApiResponse.success(PageResponse.from(products.map(ProductV1Dto.ProductResponse::from)));
    }
}
