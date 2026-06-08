package com.loopers.application.like;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;

    @Transactional
    public void addLike(Long userId, Long productId) {
        try {
            likeService.addLikeRecord(userId, productId);
            productService.increaseLikeCount(productId);
        } catch (Exception e) {
            log.warn("좋아요 등록 중 중복 요청 발생: userId={}, productId={}", userId, productId);
        }
    }

    @Transactional
    public void removeLike(Long userId, Long productId) {
        if (likeService.removeLikeRecord(userId, productId)) {
            productService.decreaseLikeCount(productId);
        }
    }
}
