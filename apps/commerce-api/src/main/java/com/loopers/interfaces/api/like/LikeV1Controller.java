package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class LikeV1Controller implements LikeV1ApiSpec {

    private final LikeApplicationService likeApplicationService;

    @PostMapping("/products/{productId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addLike(
            @PathVariable Long productId,
            @LoginUser Long userId
    ) {
        likeApplicationService.addLike(userId, productId);
    }

    @DeleteMapping("/products/{productId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeLike(
            @PathVariable Long productId,
            @LoginUser Long userId
    ) {
        likeApplicationService.removeLike(userId, productId);
    }

    @GetMapping("/users/{userId}/likes")
    public ApiResponse<PageResult<LikeV1Dto.LikeResponse>> getLikedProducts(
            @PathVariable Long userId,
            @LoginUser Long authUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (!authUserId.equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 좋아요 목록만 조회할 수 있습니다.");
        }
        return ApiResponse.success(
                PageResult.from(
                        likeApplicationService.getLikedProducts(userId, PageRequest.of(page, size))
                                .map(LikeV1Dto.LikeResponse::from)
                )
        );
    }
}
