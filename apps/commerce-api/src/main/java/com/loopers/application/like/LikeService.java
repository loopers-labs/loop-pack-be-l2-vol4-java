package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final ProductDomainService productDomainService;

    /**
     * FR-L-01. 좋아요 등록 (멱등)
     * - 존재하지 않는 상품 → 404
     * - 삭제된 상품 → 400 (ProductDomainService 위임)
     * - 이미 좋아요한 상품 → 정상 응답 (멱등)
     * - 신규 좋아요 → products.like_count atomic 증가
     */
    @Transactional
    public void like(Long userId, Long productId) {
        ProductModel product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        productDomainService.validateProductActive(product); // 삭제 상품 검증 위임

        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return; // 이미 좋아요 — 멱등 처리
        }
        likeRepository.save(new LikeModel(userId, productId));
        productRepository.incrementLikeCount(productId);
    }

    /**
     * FR-L-02. 좋아요 취소 (멱등)
     * - 이미 취소된 상태에서 재요청해도 정상 처리
     * - 좋아요 취소 → products.like_count atomic 감소 (0 미만 방지)
     */
    @Transactional
    public void unlike(Long userId, Long productId) {
        if (!likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return; // 이미 취소 상태 — 멱등 처리
        }
        likeRepository.deleteByUserIdAndProductId(userId, productId);
        productRepository.decrementLikeCount(productId);
    }

    /**
     * FR-L-03. 좋아요 목록 조회
     * - 본인의 좋아요만 조회 가능
     * - 삭제된 상품의 좋아요 항목은 목록에서 제외
     */
    @Transactional(readOnly = true)
    public List<LikeInfo> getUserLikes(Long requesterId, Long targetUserId) {
        if (!requesterId.equals(targetUserId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "좋아요 목록을 조회할 수 없습니다.");
        }
        List<LikeModel> likes = likeRepository.findAllByUserId(targetUserId);
        if (likes.isEmpty()) return List.of();

        List<Long> productIds = likes.stream().map(LikeModel::getProductId).toList();
        Map<Long, ProductModel> activeProductMap = productRepository.findAllActiveByIds(productIds)
            .stream()
            .collect(Collectors.toMap(ProductModel::getId, p -> p));

        return likes.stream()
            .filter(like -> activeProductMap.containsKey(like.getProductId()))
            .map(like -> LikeInfo.from(like, activeProductMap.get(like.getProductId())))
            .toList();
    }
}
