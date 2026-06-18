package com.loopers.application.like;

import com.loopers.application.product.ProductRepository;
import com.loopers.domain.like.ProductLikeModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@Transactional
public class LikeFacade {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    public void addLike(Long userId, Long productId) {
        ProductModel product = productRepository.findByIdWithLock(productId)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));

        if (likeRepository.findByUserIdAndProductId(userId, productId).isPresent()) {
            return;
        }

        try {
            likeRepository.save(new ProductLikeModel(userId, productId));
            product.increaseLikeCount();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            String rootMessage = e.getRootCause() != null && e.getRootCause().getMessage() != null 
                ? e.getRootCause().getMessage().toLowerCase() : "";
                
            if (message.contains("uk_product_likes_user_product") || rootMessage.contains("uk_product_likes_user_product")) {
                // Ignore unique constraint violation for idempotency
            } else {
                CoreException ex = new CoreException(ErrorType.INTERNAL_ERROR, "좋아요 등록 중 무결성 예외가 발생했습니다.");
                ex.initCause(e);
                throw ex;
            }
        }
    }

    public void removeLike(Long userId, Long productId) {
        ProductModel product = productRepository.findByIdWithLock(productId)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));

        likeRepository.findByUserIdAndProductId(userId, productId).ifPresent(like -> {
            likeRepository.delete(like);
            product.decreaseLikeCount();
        });
    }
}
