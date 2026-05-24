package com.loopers.domain.like;

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
    public Like like(Long userId, Long productId) {
        return likeRepository.findByUserIdAndProductId(userId, productId)
            .orElseGet(() -> likeRepository.save(Like.create(userId, productId)));
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
    public void unlike(Long userId, Long productId) {
        likeRepository.findByUserIdAndProductId(userId, productId)
            .ifPresent(likeRepository::delete);
    }
}
