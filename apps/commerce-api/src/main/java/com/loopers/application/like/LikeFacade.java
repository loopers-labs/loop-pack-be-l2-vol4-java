package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.like.LikeService;
import com.loopers.application.product.ProductService;
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
    public void like(LikeCommand.Like command) {
        productService.getProduct(command.productId());
        boolean added = likeService.like(command.userId(), command.productId());
        if (added) {
            productService.incrementLikeCount(command.productId());
        }
    }

    @Transactional
    public void unlike(LikeCommand.Unlike command) {
        productService.getProduct(command.productId());
        boolean removed = likeService.unlike(command.userId(), command.productId());
        if (removed) {
            productService.decrementLikeCount(command.productId());
        }
    }

    public List<ProductInfo> getLikedProducts(LikeCommand.GetLiked command) {
        if (!command.authenticatedUserId().equals(command.userId())) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 좋아요 목록만 조회할 수 있습니다.");
        }
        return likeService.getLikesByUserId(command.userId()).stream()
            .map(like -> productService.getProduct(like.getProductId()))
            .map(ProductInfo::from)
            .toList();
    }
}
