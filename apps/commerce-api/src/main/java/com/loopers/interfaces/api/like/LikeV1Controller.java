package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResult;
import com.loopers.interfaces.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class LikeV1Controller {

    private final LikeFacade likeFacade;

    @PostMapping("/products/{productId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addLike(
            @PathVariable Long productId,
            @LoginUser Long userId
    ) {
        likeFacade.addLike(userId, productId);
    }

    @DeleteMapping("/products/{productId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeLike(
            @PathVariable Long productId,
            @LoginUser Long userId
    ) {
        likeFacade.removeLike(userId, productId);
    }

    @GetMapping("/users/{userId}/likes")
    public ApiResponse<PageResult<LikeV1Dto.LikeResponse>> getLikedProducts(
            @PathVariable Long userId,
            @LoginUser Long authUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                PageResult.from(
                        likeFacade.getLikedProducts(authUserId, userId, PageRequest.of(page, size))
                                .map(LikeV1Dto.LikeResponse::from)
                )
        );
    }
}
