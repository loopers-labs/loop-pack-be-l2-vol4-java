package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeApplicationService;
import com.loopers.application.like.LikeInfo;
import com.loopers.domain.common.PageResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class LikeV1Controller implements LikeV1ApiSpec {

    private final LikeApplicationService likeApplicationService;

    @PostMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Void> register(
            @LoginUser Long userId,
            @PathVariable("productId") Long productId
    ) {
        likeApplicationService.register(userId, productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Void> cancel(
            @LoginUser Long userId,
            @PathVariable("productId") Long productId
    ) {
        likeApplicationService.cancel(userId, productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    @Override
    public ApiResponse<LikeV1Dto.LikePageResponse> getMyLikes(
            @LoginUser Long loginUserId,
            @PathVariable("userId") Long userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        if (!loginUserId.equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 좋아요 목록만 조회할 수 있습니다.");
        }
        PageResult<LikeInfo> result = likeApplicationService.getMyLikes(userId, page, size);
        return ApiResponse.success(LikeV1Dto.LikePageResponse.from(result));
    }
}
