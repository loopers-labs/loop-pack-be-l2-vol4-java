package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void like(Long userId, Long productId) {
        ProductModel product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "[id = " + productId + "] 상품을 찾을 수 없습니다."));

        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return; // 이미 좋아요 → 멱등하게 무시
        }

        likeRepository.save(new LikeModel(userId, productId));
        product.increaseLikeCount();
        productRepository.save(product);
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        ProductModel product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                        "[id = " + productId + "] 상품을 찾을 수 없습니다."));

        if (!likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return; // 좋아요 없음 → 멱등하게 무시
        }

        likeRepository.deleteByUserIdAndProductId(userId, productId);
        product.decreaseLikeCount();
        productRepository.save(product);
    }
}
