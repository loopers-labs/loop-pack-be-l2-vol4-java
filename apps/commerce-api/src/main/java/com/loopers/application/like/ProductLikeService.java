package com.loopers.application.like;

import com.loopers.domain.like.ProductLikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ProductLikeService {

    private final ProductRepository productRepository;
    private final ProductLikeRepository productLikeRepository;

    /**
     * 좋아요를 등록한다. 멱등 — 이미 좋아요 상태면 카운트 증가 없이 성공 처리한다.
     *
     * @return like_count 가 변경되었으면 true
     */
    @Transactional
    public boolean like(String userId, Long productId) {
        validateUserId(userId);
        validateProductExists(productId);

        // 유니크 제약 기반 saveIfAbsent 가 true 일 때만 증가시켜 실제 등록과 카운트 증가를 일치시킨다.
        boolean inserted = productLikeRepository.saveIfAbsent(userId, productId);
        if (inserted) {
            productRepository.incrementLikeCount(productId);
        }
        return inserted;
    }

    /**
     * 좋아요를 취소한다. 멱등 — 좋아요 상태가 아니면 카운트 감소 없이 성공 처리한다.
     *
     * @return like_count 가 변경되었으면 true
     */
    @Transactional
    public boolean unlike(String userId, Long productId) {
        validateUserId(userId);
        validateProductExists(productId);

        // 삭제된 행 수가 1일 때만 감소시켜 동시 취소 요청의 이중 감소를 차단한다.
        boolean deleted = productLikeRepository.delete(userId, productId) == 1;
        if (deleted) {
            productRepository.decrementLikeCount(productId);
        }
        return deleted;
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID 는 비어있을 수 없습니다.");
        }
    }

    private void validateProductExists(Long productId) {
        ProductModel product = productRepository.find(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        if (product.getDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다.");
        }
    }
}
