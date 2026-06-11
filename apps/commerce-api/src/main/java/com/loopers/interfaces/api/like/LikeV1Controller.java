package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class LikeV1Controller {

    private final LikeFacade likeFacade;

    @PutMapping("/{productId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> like(
            @LoginUser String loginId,
            @PathVariable(value = "productId") Long productId
    ) {
        likeFacade.like(loginId, productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{productId}/likes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> unlike(
            @LoginUser String loginId,
            @PathVariable(value = "productId") Long productId
    ) {
        likeFacade.unlike(loginId, productId);
        return ApiResponse.success(null);
    }
}