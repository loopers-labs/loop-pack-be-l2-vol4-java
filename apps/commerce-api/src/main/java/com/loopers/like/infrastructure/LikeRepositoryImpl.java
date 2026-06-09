package com.loopers.like.infrastructure;

import com.loopers.like.domain.LikeModel;
import com.loopers.like.domain.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public LikeModel save(LikeModel like) {
        return likeJpaRepository.save(like);
    }

    @Override
    public boolean exists(Long memberId, Long productId) {
        return likeJpaRepository.existsByMemberIdAndProductId(memberId, productId);
    }

    @Override
    public void delete(Long memberId, Long productId) {
        likeJpaRepository.findByMemberIdAndProductId(memberId, productId)
            .ifPresent(likeJpaRepository::delete);
    }

    @Override
    public long countByProductId(Long productId) {
        return likeJpaRepository.countByProductId(productId);
    }

    @Override
    public List<LikeModel> findByMemberId(Long memberId) {
        return likeJpaRepository.findByMemberId(memberId);
    }
}
