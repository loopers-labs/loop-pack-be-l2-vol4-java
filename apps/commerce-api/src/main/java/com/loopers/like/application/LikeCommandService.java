package com.loopers.like.application;

import com.loopers.like.domain.LikeChange;
import com.loopers.like.domain.LikeService;
import com.loopers.like.domain.ProductLikeCountChange;
import com.loopers.like.domain.ProductLikeCountChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeCommandService {

    private final LikeService likeService;
    private final ProductLikeCountChangeRepository productLikeCountChangeRepository;

    @Transactional
    public void like(Long userId, Long productId) {
        LikeChange change = likeService.like(userId, productId);
        if (change.hasCountChange()) {
            productLikeCountChangeRepository.save(ProductLikeCountChange.from(change));
        }
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        LikeChange change = likeService.unlike(userId, productId);
        if (change.hasCountChange()) {
            productLikeCountChangeRepository.save(ProductLikeCountChange.from(change));
        }
    }
}
