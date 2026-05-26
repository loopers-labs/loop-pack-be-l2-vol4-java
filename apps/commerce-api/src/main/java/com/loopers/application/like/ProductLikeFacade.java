package com.loopers.application.like;

import com.loopers.domain.like.ProductLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductLikeFacade {

    private final ProductLikeService productLikeService;

    public void likeProduct(String userLoginId, Long productId) {
        productLikeService.likeProduct(userLoginId, productId);
    }

    public void unlikeProduct(String userLoginId, Long productId) {
        productLikeService.unlikeProduct(userLoginId, productId);
    }
}
