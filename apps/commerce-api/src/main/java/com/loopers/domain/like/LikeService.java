package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;

    public void addLikeRecord(Long userId, Long productId) {
        try {
            likeRepository.save(new ProductLikeModel(userId, productId));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 동시 요청으로 인해 유니크 제약 조건 위반 발생 시, 이미 좋아요가 추가된 상태이므로 예외를 무시하여 멱등성 보장
        }
    }

    public boolean removeLikeRecord(Long userId, Long productId) {
        java.util.Optional<ProductLikeModel> like = likeRepository.findByUserIdAndProductId(userId, productId);
        if (like.isPresent()) {
            likeRepository.delete(like.get());
            return true;
        }
        return false;
    }

    public java.util.List<ProductLikeModel> getMyLikes(Long userId) {
        return likeRepository.findAllByUserId(userId);
    }

    public boolean existsLikeRecord(Long userId, Long productId) {
        return likeRepository.findByUserIdAndProductId(userId, productId).isPresent();
    }
}
