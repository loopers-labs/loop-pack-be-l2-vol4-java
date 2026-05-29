package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.interfaces.api.ApiResponse;
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
        @RequestAttribute("userId") Long loginUserId
    ) {
        List<LikeDto.LikeResponse> likes = likeFacade.getLikes(userId).stream()
            .map(LikeDto.LikeResponse::from)
            .toList();
        return ApiResponse.success(likes);
    }
}
