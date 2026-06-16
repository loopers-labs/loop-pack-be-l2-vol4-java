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
            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            String rootMessage = e.getRootCause() != null && e.getRootCause().getMessage() != null 
                ? e.getRootCause().getMessage().toLowerCase() : "";
                
            if (message.contains("uk_product_likes_user_product") || rootMessage.contains("uk_product_likes_user_product")) {
                // 동시 요청으로 인해 유니크 제약 조건 위반 발생 시, 이미 좋아요가 추가된 상태이므로 예외를 무시하여 멱등성 보장
            } else {
                // 그 외 무결성 예외는 장애 은닉을 막기 위해 래핑 후 전파
                com.loopers.support.error.CoreException ex = new com.loopers.support.error.CoreException(com.loopers.support.error.ErrorType.INTERNAL_ERROR, "좋아요 등록 중 무결성 예외가 발생했습니다.");
                ex.initCause(e);
                throw ex;
            }
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
