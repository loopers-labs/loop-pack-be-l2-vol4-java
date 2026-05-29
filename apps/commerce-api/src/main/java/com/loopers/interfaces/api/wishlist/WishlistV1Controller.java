package com.loopers.interfaces.api.wishlist;

import com.loopers.application.wishlist.WishlistFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class WishlistV1Controller implements WishlistV1ApiSpec {

    private final WishlistFacade wishlistFacade;

    @PostMapping("/api/v1/products/{productId}/likes")
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<Void> addLike(
            @PathVariable Long productId,
            @RequestAttribute("authenticatedUserId") Long userId
    ) {
        wishlistFacade.addLike(userId, productId);
        return ApiResponse.success((Void) null);
    }

    @DeleteMapping("/api/v1/products/{productId}/likes")
    @Override
    public ApiResponse<Void> removeLike(
            @PathVariable Long productId,
            @RequestAttribute("authenticatedUserId") Long userId
    ) {
        wishlistFacade.removeLike(userId, productId);
        return ApiResponse.success((Void) null);
    }

    @GetMapping("/api/v1/users/{userId}/likes")
    @Override
    public ApiResponse<List<WishlistV1Dto.LikedProductResponse>> getLikedProducts(
            @RequestAttribute("authenticatedUserId") Long userId
    ) {
        return ApiResponse.success(
                wishlistFacade.getLikedProducts(userId)
                        .stream()
                        .map(WishlistV1Dto.LikedProductResponse::from)
                        .toList()
        );
    }
}
