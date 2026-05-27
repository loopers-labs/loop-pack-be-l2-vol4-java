package com.loopers.infrastructure.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.projection.ProductSummary;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public LikeModel save(LikeModel like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public boolean existsByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public void deleteByUserIdAndProductId(Long userId, Long productId) {
        likeJpaRepository.deleteByUserIdAndProductId(userId, productId);
    }

    @Override
    public Page<ProductSummary> findLikedProductSummaries(Long userId, int page, int size) {
        return likeJpaRepository.findLikedProductSummaries(userId, PageRequest.of(page, size));
    }
}
