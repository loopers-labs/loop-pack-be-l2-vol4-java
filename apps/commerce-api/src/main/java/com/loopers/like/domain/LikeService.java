package com.loopers.like.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    @Transactional
    public LikeChange like(Long userId, Long productId) {
        if (likeRepository.findByUserIdAndProductId(userId, productId).isPresent()) {
            return LikeChange.unchanged(productId);
        }

        likeRepository.save(Like.create(userId, productId));
        return LikeChange.increased(productId);
    }

    @Transactional(readOnly = true)
    public long countProductLikes(Long productId) {
        return likeRepository.countByProductId(productId);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> countProductLikes(Collection<Long> productIds) {
        return likeRepository.countByProductIds(productIds);
    }

    @Transactional
    public LikeChange unlike(Long userId, Long productId) {
        return likeRepository.findByUserIdAndProductId(userId, productId)
            .map(like -> {
                likeRepository.delete(like);
                return LikeChange.decreased(productId);
            })
            .orElse(LikeChange.unchanged(productId));
    }
}
