package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;

    public void addLikeRecord(Long userId, Long productId) {
        likeRepository.save(new ProductLikeModel(userId, productId));
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
