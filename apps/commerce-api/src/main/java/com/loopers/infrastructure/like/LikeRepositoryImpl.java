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
    public Optional<LikeModel> findByMemberIdAndProductId(Long memberId, Long productId) {
        return likeJpaRepository.findByMemberIdAndProductId(memberId, productId);
    }

    @Override
    public List<LikeModel> findAllByMemberId(Long memberId) {
        return likeJpaRepository.findAllByMemberId(memberId);
    }

    @Override
    public List<LikeModel> findAllByMemberIdOrderByCreatedAtDesc(Long memberId) {
        return likeJpaRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId);
    }

    @Override
    public void delete(LikeModel like) {
        likeJpaRepository.delete(like);
    }

    @Override
    public void deleteAllByProductId(Long productId) {
        likeJpaRepository.deleteAllByProductId(productId);
    }
}
