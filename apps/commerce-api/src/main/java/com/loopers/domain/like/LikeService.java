package com.loopers.domain.like;

import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductService productService;

    @Transactional
    public void like(Long userId, Long productId) {
        productService.getById(productId);
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return;
        }
        likeRepository.save(new LikeModel(userId, productId));
        productService.incrementLikeCount(productId);
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        int deleted = likeRepository.deleteByUserIdAndProductId(userId, productId);
        if (deleted > 0) {
            productService.decrementLikeCount(productId);
        }
    }
}
