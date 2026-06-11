package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;

    @Transactional
    public void addLikeRecord(Long userId, Long productId) {
        likeRepository.save(new ProductLikeModel(userId, productId));
    }

    @Transactional
    public boolean removeLikeRecord(Long userId, Long productId) {
        if (likeRepository.findByUserIdAndProductId(userId, productId).isPresent()) {
            likeRepository.deleteByUserIdAndProductId(userId, productId);
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
