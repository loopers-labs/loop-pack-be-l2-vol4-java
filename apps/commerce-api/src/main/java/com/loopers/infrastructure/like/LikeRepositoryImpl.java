package com.loopers.infrastructure.like;

import com.loopers.application.like.LikeRepository;
import com.loopers.domain.like.ProductLikeModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public ProductLikeModel save(ProductLikeModel like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public void deleteByUserIdAndProductId(Long userId, Long productId) {
        likeJpaRepository.deleteByUserIdAndProductId(userId, productId);
    }

    @Override
    public void delete(ProductLikeModel like) {
        likeJpaRepository.delete(like);
    }

    @Override
    public List<ProductLikeModel> findAllByUserId(Long userId) {
        return likeJpaRepository.findAllByUserId(userId);
    }

    @Override
    public Optional<ProductLikeModel> findByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public int countByProductId(Long productId) {
        return (int) likeJpaRepository.countByProductId(productId);
    }

    @Override
    public java.util.Map<Long, Integer> countByProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return java.util.Collections.emptyMap();
        List<Object[]> results = likeJpaRepository.countByProductIds(productIds);
        return results.stream().collect(java.util.stream.Collectors.toMap(
                row -> (Long) row[0],
                row -> ((Long) row[1]).intValue()
        ));
    }
}
