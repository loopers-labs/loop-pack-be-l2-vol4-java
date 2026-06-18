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
    public boolean like(Long memberId, Long productId) {
        if (likeRepository.findActiveLike(memberId, productId).isPresent()) {
            return false;
        }
        likeRepository.save(new LikeModel(memberId, productId));
        return true;
    }

    @Transactional
    public boolean unlike(Long memberId, Long productId) {
        return likeRepository.findActiveLike(memberId, productId)
                .map(like -> { like.delete(); return true; })
                .orElse(false);
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