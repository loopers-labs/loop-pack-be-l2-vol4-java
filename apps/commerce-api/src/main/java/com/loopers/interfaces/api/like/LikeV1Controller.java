package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.like.LikeInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class LikeV1Controller {

    private final LikeFacade likeFacade;

    @PostMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Object> addLike(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @PathVariable Long productId
    ) {
        likeFacade.addLike(loginId, productId);
        return ApiResponse.success();
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Object> removeLike(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @PathVariable Long productId
    ) {
        likeFacade.removeLike(loginId, productId);
        return ApiResponse.success();
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    public ApiResponse<List<LikeV1Dto.LikeResponse>> getLikes(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @PathVariable Long userId
    ) {
        List<LikeInfo> likes = likeFacade.getLikes(loginId, userId);
        return ApiResponse.success(likes.stream().map(LikeV1Dto.LikeResponse::from).toList());
    }
}
