package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
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
    public long countByProductId(Long productId) {
        return likeJpaRepository.countByProductId(productId);
    }

    @Override
    public Map<Long, Long> countByProductIds(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return likeJpaRepository.countByProductIds(productIds).stream()
            .collect(Collectors.toMap(
                LikeJpaRepository.LikeCount::getProductId,
                LikeJpaRepository.LikeCount::getLikeCount
            ));
    }

    @Override
    public void delete(Like like) {
        likeJpaRepository.delete(like);
    }
}
