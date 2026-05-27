package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeService;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class LikeV1Controller {

    private final LikeService likeService;

    /** FR-L-01. 좋아요 등록 (멱등) */
    @PostMapping("/products/{productId}/likes")
    public ApiResponse<Object> like(
        @PathVariable Long productId,
        @CurrentUser UserModel currentUser
    ) {
        likeService.like(currentUser.getId(), productId);
        return ApiResponse.success();
    }

    /** FR-L-02. 좋아요 취소 (멱등) */
    @DeleteMapping("/products/{productId}/likes")
    public ApiResponse<Object> unlike(
        @PathVariable Long productId,
        @CurrentUser UserModel currentUser
    ) {
        likeService.unlike(currentUser.getId(), productId);
        return ApiResponse.success();
    }

    /** FR-L-03. 내가 좋아요한 상품 목록 조회 */
    @GetMapping("/users/{userId}/likes")
    public ApiResponse<List<LikeV1Dto.LikeResponse>> getUserLikes(
        @PathVariable Long userId,
        @CurrentUser UserModel currentUser
    ) {
        List<LikeV1Dto.LikeResponse> responses = likeService.getUserLikes(currentUser.getId(), userId)
            .stream()
            .map(LikeV1Dto.LikeResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
