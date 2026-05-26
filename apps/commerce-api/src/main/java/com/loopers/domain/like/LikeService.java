package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
@Service
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    /**
     * 좋아요 등록 - 멱등 동작 (요구사항 P-1)
     *
     * - 상품이 없거나 삭제된 경우 → 404 (멱등 대상 아님, P-3)
     * - 이미 좋아요한 경우 → 추가 작업 없이 정상 종료 (멱등)
     * - 동시 요청으로 UK 위반이 발생한 경우 → 예외를 잡고 정상 종료 (멱등)
     */
    @Transactional
    public void like(Long userId, Long productId) {
        productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));

        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return;
        }

        try {
            likeRepository.save(LikeModel.of(userId, productId));
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 UK 위반 - 다른 요청이 이미 좋아요를 등록 완료. 멱등성 보장.
        }
    }

    /**
     * 좋아요 취소 - 멱등 동작 (요구사항 P-2)
     *
     * 좋아요 레코드가 없어도 정상 종료한다. 삭제된 상품에 걸린 좋아요도 취소 가능.
     */
    @Transactional
    public void unlike(Long userId, Long productId) {
        likeRepository.delete(userId, productId);
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getLikedProducts(Long userId) {
        List<LikeModel> likes = likeRepository.findByUserId(userId);
        return likes.stream()
            .map(like -> productRepository.findById(like.getProductId()).orElse(null))
            .filter(Objects::nonNull)
            .toList();
    }

    /** 상품의 좋아요 수 집계. 상품 상세/목록 응답 구성에 활용. */
    @Transactional(readOnly = true)
    public long countByProductId(Long productId) {
        return likeRepository.countByProductId(productId);
    }

    /**
     * 여러 상품의 좋아요 수를 일괄 집계. 목록 조회의 N+1 회피용.
     *
     * @return {@code productId → count} 맵. 좋아요가 0개인 상품은 맵에 포함되지 않으므로
     *         호출 측에서 {@code getOrDefault(id, 0L)} 로 안전하게 조회한다.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> countByProductIdIn(List<Long> productIds) {
        return likeRepository.countByProductIdIn(productIds);
    }
}
