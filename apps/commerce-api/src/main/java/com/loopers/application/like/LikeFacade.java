package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.like.LikeService;
import com.loopers.application.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void like(LikeCommand.Like command) {
        productService.getProduct(command.productId());
        boolean added = likeService.like(command.userId(), command.productId());
        if (added) {
            eventPublisher.publishEvent(new LikeCountChangedEvent(command.productId(), true));
        }
    }

    @Transactional
    public void unlike(LikeCommand.Unlike command) {
        productService.getProduct(command.productId());
        boolean removed = likeService.unlike(command.userId(), command.productId());
        if (removed) {
            eventPublisher.publishEvent(new LikeCountChangedEvent(command.productId(), false));
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
