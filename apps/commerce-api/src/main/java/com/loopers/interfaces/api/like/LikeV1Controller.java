package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.like.LikedProductInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class LikeV1Controller implements LikeV1ApiSpec {

    private final LikeFacade likeFacade;

    @PostMapping("/products/{productId}/likes")
    @Override
    public ApiResponse<Void> like(
        @AuthenticatedUser LoginUser loginUser,
        @PathVariable("productId") Long productId
    ) {
        likeFacade.like(loginUser.id(), productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/products/{productId}/likes")
    @Override
    public ApiResponse<Void> unlike(
        @AuthenticatedUser LoginUser loginUser,
        @PathVariable("productId") Long productId
    ) {
        likeFacade.unlike(loginUser.id(), productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/me/likes")
    @Override
    public ApiResponse<List<LikeV1Dto.LikedProductResponse>> getMyLikes(
        @AuthenticatedUser LoginUser loginUser,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        List<LikedProductInfo> infos = likeFacade.getMyLikes(loginUser.id(), page, size);
        List<LikeV1Dto.LikedProductResponse> responses = infos.stream()
            .map(LikeV1Dto.LikedProductResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }
}
