package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;

    @Transactional
    public void like(Long userId, Long productId) {
        boolean added = likeService.addLike(userId, productId);
        if (added) {
            productService.incrementLikeCount(productId);
        }
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        boolean removed = likeService.removeLike(userId, productId);
        if (removed) {
            productService.decrementLikeCount(productId);
        }
    }

    public List<ProductInfo> getLikedProducts(Long userId) {
        return likeService.getLikedProducts(userId).stream()
            .map(like -> {
                try {
                    return ProductInfo.from(productService.getProduct(like.getProductId()));
                } catch (CoreException e) {
                    if (e.getErrorType() == ErrorType.NOT_FOUND) return null;
                    throw e;
                }
            })
            .filter(info -> info != null)
            .toList();
    }
}
