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
    public boolean addLike(Long userId, Long productId) {
        if (likeRepository.existsBy(userId, productId)) return false;
        likeRepository.save(new Like(userId, productId));
        return true;
    }

    @Transactional
    public boolean removeLike(Long userId, Long productId) {
        if (!likeRepository.existsBy(userId, productId)) return false;
        likeRepository.deleteBy(userId, productId);
        return true;
    }

    @Transactional(readOnly = true)
    public List<Like> getLikedProducts(Long userId) {
        return likeRepository.findByUserId(userId);
    }

    @Transactional
    public void bulkDeleteByProductIds(List<Long> productIds) {
        if (productIds.isEmpty()) return;
        likeRepository.deleteAllByProductIdIn(productIds);
    }
}
