package com.loopers.interfaces.api.like;

import com.loopers.application.like.ProductLikeFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products/{productId}/likes")
public class LikeV1Controller {

    private final ProductLikeFacade productLikeFacade;

    @PostMapping
    public ApiResponse<LikeV1Dto.LikeResponse> like(
        @RequestHeader("X-USER-ID") String userId,
        @PathVariable("productId") Long productId
    ) {
        productLikeFacade.like(userId, productId);
        return ApiResponse.success(new LikeV1Dto.LikeResponse(productId, true));
    }

    @DeleteMapping
    public ApiResponse<LikeV1Dto.LikeResponse> unlike(
        @RequestHeader("X-USER-ID") String userId,
        @PathVariable("productId") Long productId
    ) {
        productLikeFacade.unlike(userId, productId);
        return ApiResponse.success(new LikeV1Dto.LikeResponse(productId, false));
    }
}
