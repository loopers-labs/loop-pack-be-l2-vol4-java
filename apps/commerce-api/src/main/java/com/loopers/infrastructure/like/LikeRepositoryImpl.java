package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeEntity;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public LikeEntity save(LikeEntity like) {
        return LikeMapper.toDomain(likeJpaRepository.save(LikeMapper.toJpaEntity(like)));
    }

    @Override
    public Optional<LikeEntity> findActive(String userId, String productId) {
        return likeJpaRepository.findByUserIdAndProductIdAndDeletedAtIsNull(userId, productId)
                .map(LikeMapper::toDomain);
    }

    @Override
    public Optional<LikeEntity> findAny(String userId, String productId) {
        return likeJpaRepository.findByUserIdAndProductId(userId, productId)
                .map(LikeMapper::toDomain);
    }

    @Override
    public Page<LikeEntity> findActiveByUserId(String userId, Pageable pageable) {
        return likeJpaRepository.findAllByUserIdAndDeletedAtIsNull(userId, pageable)
                .map(LikeMapper::toDomain);
    }

    @Override
    public void deleteAllByProductId(String productId) {
        likeJpaRepository.softDeleteAllByProductId(productId, ZonedDateTime.now());
    }

    @Override
    public void deleteAllByProductIds(List<String> productIds) {
        likeJpaRepository.softDeleteAllByProductIds(productIds, ZonedDateTime.now());
    }
}
