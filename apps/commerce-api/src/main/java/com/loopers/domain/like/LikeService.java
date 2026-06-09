package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    @Transactional
    public void like(Long memberId, Long productId) {
        boolean alreadyLiked = likeRepository.findActiveLike(memberId, productId).isPresent();
        if (alreadyLiked) {
            return;
        }
        likeRepository.save(new LikeModel(memberId, productId));
    }

    @Transactional
    public void unlike(Long memberId, Long productId) {
        likeRepository.findActiveLike(memberId, productId)
                .ifPresent(LikeModel::delete);
    }

    @Transactional(readOnly = true)
    public List<LikeModel> getLikedByMember(Long memberId) {
        return likeRepository.findAllActiveByMemberId(memberId);
    }

    @Transactional(readOnly = true)
    public long countLikes(Long productId) {
        return likeRepository.countActiveByProductId(productId);
    }
}