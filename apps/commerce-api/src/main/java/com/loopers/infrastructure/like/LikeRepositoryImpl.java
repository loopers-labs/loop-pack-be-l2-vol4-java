package com.loopers.infrastructure.like;

import com.loopers.domain.common.PageResult;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public int save(Like like) {
        return likeJpaRepository.insertIgnore(like.getUserId(), like.getProductId());
    }

    @Override
    public int delete(Long userId, Long productId) {
        return likeJpaRepository.deleteByUserIdAndProductId(userId, productId);
    }

    @Override
    public boolean exists(Long userId, Long productId) {
        return likeJpaRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public long countByProductId(Long productId) {
        return likeJpaRepository.countByProductId(productId);
    }

    @Override
    public PageResult<Like> findAllByUserId(Long userId, int page, int size) {
        Page<Like> result = likeJpaRepository.findAllByUserId(
                userId,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt")))
        );
        return new PageResult<>(result.getContent(), page, size, result.hasNext(), result.getTotalElements());
    }
}
