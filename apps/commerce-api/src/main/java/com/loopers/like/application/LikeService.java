package com.loopers.like.application;

import com.loopers.like.domain.Like;
import com.loopers.like.domain.LikeRepository;
import com.loopers.product.application.ProductReader;
import com.loopers.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final ProductReader productReader;

    @Transactional
    public void register(Long userId, Long productId) {
        productReader.getActive(productId);
        likeRepository.findByUserIdAndProductId(userId, productId)
                .ifPresentOrElse(
                        Like::restore,
                        () -> likeRepository.save(Like.create(userId, productId))
                );
    }

    @Transactional
    public void cancel(Long userId, Long productId) {
        likeRepository.findByUserIdAndProductId(userId, productId)
                .ifPresent(Like::delete);
    }

    @Transactional(readOnly = true)
    public List<LikeResult.LikedProduct> getMyLikes(Long userId) {
        List<Like> likes = likeRepository.findActiveByUserId(userId);
        if (likes.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = likes.stream().map(Like::getProductId).toList();
        return productRepository.findAllByIdIn(productIds).stream()
                .map(LikeResult.LikedProduct::from)
                .toList();
    }
}
