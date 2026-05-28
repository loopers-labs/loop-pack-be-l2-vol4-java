package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products/{productId}/likes")
public class LikeV1Controller implements LikeV1ApiSpec {

    private final LikeFacade likeFacade;

    @PostMapping
    @Override
    public ApiResponse<Object> like(
            @RequestHeader(value = "X-Loopers-UserId", required = false) Long userId,
            @PathVariable Long productId
    ) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "X-Loopers-UserId 헤더가 필요합니다.");
        }
        likeFacade.like(userId, productId);
        return ApiResponse.success();
    }

    @DeleteMapping
    @Override
    public ApiResponse<Object> unlike(
            @RequestHeader(value = "X-Loopers-UserId", required = false) Long userId,
            @PathVariable Long productId
    ) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "X-Loopers-UserId 헤더가 필요합니다.");
        }
        likeFacade.unlike(userId, productId);
        return ApiResponse.success();
    }
}
