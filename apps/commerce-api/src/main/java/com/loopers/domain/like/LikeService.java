package com.loopers.domain.like;

import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductService productService;

    @Transactional
    public void like(Long userId, Long productId) {
        LikeId id = LikeId.of(userId, productId);
        Optional<LikeModel> likeModel = likeRepository.find(id);
        if (likeModel.isEmpty()) {
            likeRepository.save(LikeModel.of(userId, productId));
            productService.increaseLikeCount(productId);
            return;
        }
        LikeModel like = likeModel.get();
        if (!like.isLiked()) {
            like.like();
            productService.increaseLikeCount(productId);
        }
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        LikeId id = LikeId.of(userId, productId);
        Optional<LikeModel> likeModel = likeRepository.find(id);
        likeModel.ifPresent(like -> {
            if (like.isLiked()) {
                like.unlike();
                productService.decreaseLikeCount(productId);
            }
        });
    }
}