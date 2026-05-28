package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public LikeModel save(LikeModel like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public Optional<LikeModel> findActiveLike(Long memberId, Long productId) {
        return likeJpaRepository.findByMemberIdAndProductIdAndDeletedAtIsNull(memberId, productId);
    }

    @Override
    public List<LikeModel> findAllActiveByMemberId(Long memberId) {
        return likeJpaRepository.findAllByMemberIdAndDeletedAtIsNull(memberId);
    }

    @Override
    public long countActiveByProductId(Long productId) {
        return likeJpaRepository.countByProductIdAndDeletedAtIsNull(productId);
    }
}