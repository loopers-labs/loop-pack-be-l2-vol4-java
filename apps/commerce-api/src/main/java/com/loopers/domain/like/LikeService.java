package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    @Transactional
    public boolean like(Long userId, Long productId) {
        Optional<Like> existing = likeRepository.findByUserIdAndProductId(userId, productId);
        if (existing.isPresent()) {
            Like like = existing.get();
            if (like.getDeletedAt() != null) {
                like.restore();
                return true;
            }
            return false;
        }
        likeRepository.save(new Like(userId, productId));
        return true;
    }

    @Transactional
    public boolean unlike(Long userId, Long productId) {
        Optional<Like> existing = likeRepository.findByUserIdAndProductId(userId, productId);
        if (existing.isEmpty() || existing.get().getDeletedAt() != null) {
            return false;
        }
        existing.get().delete();
        return true;
    }
}
