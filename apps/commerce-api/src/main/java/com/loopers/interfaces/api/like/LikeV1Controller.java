package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeApplicationService;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.interceptor.AuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RequiredArgsConstructor
@RestController
public class LikeV1Controller {

    private final LikeApplicationService likeApplicationService;

    @PostMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Void> like(@PathVariable @Min(1) Long productId, HttpServletRequest request) {
        UserModel user = (UserModel) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
        likeApplicationService.like(user.getId(), productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Void> unlike(@PathVariable @Min(1) Long productId, HttpServletRequest request) {
        UserModel user = (UserModel) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
        likeApplicationService.unlike(user.getId(), productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    public ApiResponse<LikeV1Dto.LikedProductListResponse> getLikedProducts(
        @PathVariable @Min(1) Long userId,
        HttpServletRequest request
    ) {
        UserModel user = (UserModel) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
        if (!user.getId().equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN);
        }
        return ApiResponse.success(LikeV1Dto.LikedProductListResponse.from(likeApplicationService.getLikedProducts(userId)));
    }
}
