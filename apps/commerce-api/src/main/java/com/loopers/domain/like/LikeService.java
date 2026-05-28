package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    @Transactional
    public LikeEntity like(Long userId, Long productId) {
        Optional<LikeEntity> existing = likeRepository.findAny(userId, productId);
        if (existing.isPresent()) {
            LikeEntity like = existing.get();
            if (!like.isDeleted()) {
                throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
            }
            like.restore();
            return likeRepository.save(like);
        }
        return likeRepository.save(new LikeEntity(userId, productId));
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        LikeEntity like = likeRepository.findActive(userId, productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요 정보를 찾을 수 없습니다."));
        like.delete();
        likeRepository.save(like);
    }

    @Transactional(readOnly = true)
    public Page<LikeEntity> getLikedProducts(Long userId, Pageable pageable) {
        return likeRepository.findActiveByUserId(userId, pageable);
    }

    @Transactional
    public void deleteAllByProduct(Long productId) {
        likeRepository.deleteAllByProductId(productId);
    }

    @Transactional
    public void deleteAllByProducts(List<Long> productIds) {
        likeRepository.deleteAllByProductIds(productIds);
    }
}
