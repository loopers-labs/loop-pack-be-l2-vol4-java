package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthHeaders;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class LikeV1Controller {

    private final LikeFacade likeFacade;
    private final UserFacade userFacade;

    @PostMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Void> like(
        @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
        @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw,
        @PathVariable Long productId
    ) {
        UserInfo user = userFacade.login(loginId, loginPw);
        likeFacade.like(user.id(), productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    public ApiResponse<Void> unlike(
        @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
        @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw,
        @PathVariable Long productId
    ) {
        UserInfo user = userFacade.login(loginId, loginPw);
        likeFacade.unlike(user.id(), productId);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    public ApiResponse<List<ProductV1Dto.ProductResponse>> getLikedProducts(
        @RequestHeader(AuthHeaders.LOGIN_ID) String loginId,
        @RequestHeader(AuthHeaders.LOGIN_PW) String loginPw,
        @PathVariable Long userId
    ) {
        UserInfo user = userFacade.login(loginId, loginPw);
        if (!user.id().equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "다른 유저의 좋아요 목록에 접근할 수 없습니다.");
        }
        List<ProductInfo> products = likeFacade.getLikedProducts(user.id());
        return ApiResponse.success(products.stream().map(ProductV1Dto.ProductResponse::from).toList());
    }
}
