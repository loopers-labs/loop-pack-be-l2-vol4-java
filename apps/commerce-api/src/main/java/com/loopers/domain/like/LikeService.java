package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void like(Long userId, Long productId) {
        if (likeRepository.existsBy(userId, productId)) return;
        likeRepository.save(new Like(userId, productId));
        ProductModel product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        product.incrementLikeCount();
        productRepository.save(product);
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        if (!likeRepository.existsBy(userId, productId)) return;
        likeRepository.deleteBy(userId, productId);
        ProductModel product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        product.decrementLikeCount();
        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public List<Like> getLikedProducts(Long userId) {
        return likeRepository.findByUserId(userId);
    }
}
