package com.loopers.application.like;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LikeFacade {

    private final LikeService likeService;
    private final UserService userService;
    private final ProductService productService;

    public void like(String loginId, Long productId) {
        UserModel user = userService.getMyInfo(loginId);
        productService.getProduct(productId);
        likeService.like(user.getId(), productId);
    }

    public void unlike(String loginId, Long productId) {
        UserModel user = userService.getMyInfo(loginId);
        productService.getProduct(productId);
        likeService.unlike(user.getId(), productId);
    }
}