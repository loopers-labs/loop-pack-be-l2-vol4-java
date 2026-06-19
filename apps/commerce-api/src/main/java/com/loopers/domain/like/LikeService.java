package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 좋아요 도메인 서비스.
 * 좋아요 수는 별도 컬럼으로 관리하지 않고 Like 집계(count)로 계산한다.
 * 등록/취소는 멱등하게 동작한다.
 */
@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    @Transactional
    public void like(Long userId, Long productId) {
        if (likeRepository.existsBy(userId, productId)) {
            return; // 이미 좋아요한 상태이면 멱등하게 아무 동작도 하지 않는다.
        }
        likeRepository.save(Like.of(userId, productId));
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        if (!likeRepository.existsBy(userId, productId)) {
            return; // 좋아요하지 않은 상태이면 멱등하게 아무 동작도 하지 않는다.
        }
        likeRepository.deleteBy(userId, productId);
    }

    @Transactional(readOnly = true)
    public long getLikeCount(Long productId) {
        return likeRepository.countByProductId(productId);
    }

    @Transactional(readOnly = true)
    public List<Long> getLikedProductIds(Long userId) {
        return likeRepository.findProductIdsByUserId(userId);
    }
}
