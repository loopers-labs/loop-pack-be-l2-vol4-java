package com.loopers.application.like;
        // 2. 이미 좋아요를 눌렀는지 확인 (멱등성 보장)
import com.loopers.application.product.ProductRepository;
import com.loopers.application.like.LikeRepository;
import com.loopers.domain.like.ProductLikeModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
        // 3. 좋아요 추가
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeFacade {
        // 2. 좋아요가 존재하는 경우에만 삭제 (멱등성 보장)
    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
        // 3. 좋아요 삭제
    public void addLike(Long userId, Long productId) {
        productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));

        if (likeRepository.findByUserIdAndProductId(userId, productId).isPresent()) {
            return;
        }

        try {
            likeRepository.save(new ProductLikeModel(userId, productId));
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
        productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));

        likeRepository.findByUserIdAndProductId(userId, productId).ifPresent(likeRepository::delete);
    }
}
