package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    @Transactional
    public Like addLike(Long userId, Long productId) {
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }
        return likeRepository.save(new Like(userId, productId));
    }

    @Transactional
    public void removeLike(Long userId, Long productId) {
        Like like = likeRepository.findByUserIdAndProductId(userId, productId)
            .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, "좋아요하지 않은 상품입니다."));
        likeRepository.delete(like.getId());
    }

    @Transactional(readOnly = true)
    public List<Long> getLikedProductIds(Long userId) {
        return likeRepository.findAllByUserId(userId).stream()
            .map(Like::getProductId)
            .toList();
    }

    @Transactional(readOnly = true)
    public boolean isLiked(Long userId, Long productId) {
        return likeRepository.existsByUserIdAndProductId(userId, productId);
    }
}
