package com.loopers.interfaces.api.productlike;

import com.loopers.application.productlike.ProductLikeFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
public class ProductLikeV1Controller implements ProductLikeV1ApiSpec {

    private final ProductLikeFacade productLikeFacade;

    @PostMapping("/products/{productId}/likes")
    @Override
    public ApiResponse<Object> like(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @PathVariable("productId") Long productId
    ) {
        productLikeFacade.like(loginId, loginPw, productId);
        return ApiResponse.success();
    }

    @DeleteMapping("/products/{productId}/likes")
    @Override
    public ApiResponse<Object> unlike(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @PathVariable("productId") Long productId
    ) {
        productLikeFacade.unlike(loginId, loginPw, productId);
        return ApiResponse.success();
    }

    @GetMapping("/users/{userId}/likes")
    @Override
    public ApiResponse<ProductLikeV1Dto.LikedProductsResponse> getLikedProducts(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @PathVariable("userId") String userId
    ) {
        ProductLikeV1Dto.LikedProductsResponse response = ProductLikeV1Dto.LikedProductsResponse.from(
            productLikeFacade.getLikedProducts(loginId, loginPw, userId)
        );
        return ApiResponse.success(response);
    }
}
