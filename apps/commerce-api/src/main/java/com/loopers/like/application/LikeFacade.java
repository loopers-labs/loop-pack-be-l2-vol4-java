package com.loopers.like.application;

import com.loopers.like.domain.LikeModel;
import com.loopers.like.domain.LikeRepository;
import com.loopers.like.domain.LikeService;
import com.loopers.product.application.ProductInfo;
import com.loopers.product.domain.ProductRepository;
import com.loopers.product.domain.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final LikeRepository likeRepository;
    private final ProductService productService;
    private final ProductRepository productRepository;

    @Transactional
    public LikeInfo addLike(Long userId, Long productId) {
        productService.getOrThrow(productRepository.find(productId));

        Optional<LikeModel> existing = likeRepository.findByUserIdAndProductId(userId, productId);
        LikeModel like = likeService.createLike(existing, userId, productId);
        LikeInfo saved = LikeInfo.from(likeRepository.save(like));
        productRepository.incrementLikeCount(productId);
        return saved;
    }

    @Transactional
    public void cancelLike(Long userId, Long productId) {
        LikeModel like = likeService.cancelLike(likeRepository.findByUserIdAndProductId(userId, productId));
        likeRepository.delete(like);
        productRepository.decrementLikeCount(productId);
    }

    @Transactional(readOnly = true)
    public List<ProductInfo> getLikedProducts(Long userId) {
        List<LikeModel> likes = likeRepository.findAllByUserId(userId);
        List<Long> productIds = likes.stream().map(LikeModel::getProductId).toList();
        // [fix] N+1 문제 → productId 목록으로 IN 쿼리 일괄 조회 (결정 10)
        return productRepository.findAllByIds(productIds).stream()
            .map(ProductInfo::from)
            .toList();
    }
}
