package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    public List<Long> getLikedProductIds(Long userId) {
        return likeRepository.findAllByUserId(userId).stream()
                .map(LikeModel::getProductId)
                .toList();
    }

    public LikeResult register(Long userId, Long productId) {
        return likeRepository.save(new LikeModel(userId, productId))
                ? LikeResult.APPLIED
                : LikeResult.IGNORED;
    }

    public LikeResult cancel(Long userId, Long productId) {
        return likeRepository.deleteByUserIdAndProductId(userId, productId)
                ? LikeResult.APPLIED
                : LikeResult.IGNORED;
    }
}
