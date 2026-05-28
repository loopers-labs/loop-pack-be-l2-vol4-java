package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    public Optional<LikeModel> find(UUID userId, UUID productId) {
        return likeRepository.findByUserIdAndProductId(userId, productId);
    }

    /** 좋아요 저장 — 이미 존재하면 기존 반환 (멱등) */
    public LikeModel like(UUID userId, UUID productId) {
        return likeRepository.findByUserIdAndProductId(userId, productId)
            .orElseGet(() -> likeRepository.save(new LikeModel(userId, productId)));
    }

    /**
     * 좋아요 취소 — 존재하면 삭제 후 true, 없으면 false (멱등)
     * Facade에서 true일 때만 likeCount 차감
     */
    public boolean unlike(UUID userId, UUID productId) {
        if (!likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return false;
        }
        likeRepository.deleteByUserIdAndProductId(userId, productId);
        return true;
    }

    public Page<LikeModel> findAllByUserId(UUID userId, Pageable pageable) {
        return likeRepository.findAllByUserId(userId, pageable);
    }
}
