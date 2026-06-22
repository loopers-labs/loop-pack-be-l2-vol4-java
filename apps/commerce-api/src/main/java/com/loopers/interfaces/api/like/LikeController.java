package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.domain.like.LikeSort;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class LikeController {

    private final LikeFacade likeFacade;

    @PostMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Void> like(
        @PathVariable Long productId,
        @RequestAttribute("userId") Long userId
    ) {
        likeFacade.like(userId, productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Void> unlike(
        @PathVariable Long productId,
        @RequestAttribute("userId") Long userId
    ) {
        likeFacade.unlike(userId, productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    public ApiResponse<List<LikeDto.LikeResponse>> getLikes(
        @PathVariable Long userId,
        @RequestAttribute("userId") Long loginUserId,
        @RequestParam(defaultValue = "latest") LikeSort sort
    ) {
        if (!userId.equals(loginUserId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 좋아요 목록만 조회할 수 있습니다.");
        }
        List<LikeDto.LikeResponse> likes = likeFacade.getLikes(userId, sort).stream()
            .map(LikeDto.LikeResponse::from)
            .toList();
        return ApiResponse.success(likes);
    }
}
