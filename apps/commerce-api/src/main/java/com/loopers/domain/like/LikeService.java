package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final LikeEventPublisher eventPublisher;

    public List<Long> getLikedProductIds(Long userId) {
        return likeRepository.findAllByUserId(userId).stream()
                .map(LikeModel::getProductId)
                .toList();
    }

    public LikeResult register(Long userId, Long productId) {
        if (!likeRepository.save(new LikeModel(userId, productId))) {
            return LikeResult.IGNORED;
        }
        eventPublisher.publish(new LikedEvent(UUID.randomUUID().toString(), userId, productId));
        return LikeResult.APPLIED;
    }

    public LikeResult cancel(Long userId, Long productId) {
        if (!likeRepository.deleteByUserIdAndProductId(userId, productId)) {
            return LikeResult.IGNORED;
        }
        eventPublisher.publish(new UnlikedEvent(UUID.randomUUID().toString(), userId, productId));
        return LikeResult.APPLIED;
    }
}
