package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void like(Long userId, Long productId) {
        if (likeRepository.existsBy(userId, productId)) {
            return; // 멱등: 이미 좋아요한 경우
        }
        ProductModel product = productRepository.find(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));

        likeRepository.save(new LikeModel(userId, productId));
        product.increaseLikeCount();
        productRepository.save(product);
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        if (!likeRepository.existsBy(userId, productId)) {
            return; // 멱등: 좋아요하지 않은 경우
        }
        likeRepository.deleteBy(userId, productId);
        productRepository.find(productId).ifPresent(product -> {
            product.decreaseLikeCount();
            productRepository.save(product);
        });
    }
}
