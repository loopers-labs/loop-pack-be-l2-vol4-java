package com.loopers.infrastructure.productlike;

import com.loopers.domain.productlike.ProductLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductLikeRepositoryImpl implements ProductLikeRepository {

    private final ProductLikeJpaRepository productLikeJpaRepository;

    @Override
    public int insertIgnore(Long userId, Long productId) {
        return productLikeJpaRepository.insertIgnore(userId, productId);
    }

    @Override
    public int deleteByUserIdAndProductId(Long userId, Long productId) {
        return productLikeJpaRepository.deleteByUserIdAndProductId(userId, productId);
    }

    @Override
    public List<Long> findLikedProductIds(Long userId) {
        return productLikeJpaRepository.findProductIdsByUserId(userId);
    }
}
