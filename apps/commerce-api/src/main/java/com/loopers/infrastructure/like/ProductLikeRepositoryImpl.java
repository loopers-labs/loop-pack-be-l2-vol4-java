package com.loopers.infrastructure.like;

import com.loopers.domain.like.ProductLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductLikeRepositoryImpl implements ProductLikeRepository {
    private final ProductLikeJpaRepository productLikeJpaRepository;

    @Override
    public boolean saveIfAbsent(String userId, Long productId) {
        return productLikeJpaRepository.insertIgnore(userId, productId) == 1;
    }

    @Override
    public int delete(String userId, Long productId) {
        return productLikeJpaRepository.deleteByUserIdAndProductId(userId, productId);
    }
}
