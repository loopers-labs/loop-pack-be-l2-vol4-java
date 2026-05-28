package com.loopers.application.like;

import com.loopers.domain.like.ProductLike;
import com.loopers.domain.like.ProductLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final ProductLikeRepository productLikeRepository;

    public boolean like(Long userId, Long productId) {
        if (productLikeRepository.existsByUserIdAndProductId(userId, productId)) {
            return false;
        }
        productLikeRepository.save(new ProductLike(userId, productId));
        return true;
    }

    public boolean unlike(Long userId, Long productId) {
        if (!productLikeRepository.existsByUserIdAndProductId(userId, productId)) {
            return false;
        }
        productLikeRepository.deleteByUserIdAndProductId(userId, productId);
        return true;
    }

    public List<ProductLike> getLikesByUserId(Long userId) {
        return productLikeRepository.findAllByUserId(userId);
    }
}
