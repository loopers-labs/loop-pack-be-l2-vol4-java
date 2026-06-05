package com.loopers.like.infrastructure;

import com.loopers.like.domain.Like;
import com.loopers.like.domain.LikeRepository;
import com.loopers.like.infrastructure.LikeJpaRepository.LikeCountProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public Like save(Like like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public Optional<Like> findByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public List<Like> findActiveByUserId(Long userId) {
        return likeJpaRepository.findAllByUserIdAndDeletedAtIsNull(userId);
    }

    @Override
    public long countActiveByProductId(Long productId) {
        return likeJpaRepository.countByProductIdAndDeletedAtIsNull(productId);
    }

    @Override
    public Map<Long, Long> countActiveByProductIds(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return likeJpaRepository.countActiveByProductIds(productIds).stream()
                .collect(Collectors.toMap(LikeCountProjection::getProductId, LikeCountProjection::getLikeCount));
    }
}
