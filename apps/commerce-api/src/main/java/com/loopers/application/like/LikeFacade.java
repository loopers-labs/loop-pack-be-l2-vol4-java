package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 좋아요 유스케이스 Facade.
 *
 * <p>스타일 2 (DDD 정통): Application Layer 가 조회·검증·저장을 책임지고,
 * 도메인 객체(LikeModel)는 단순 값 보유. Domain Service 는 두지 않는다.
 * 좋아요 등록/취소는 도메인 협력 로직이 없는 단순 유스케이스라 별도 Domain Service 가 불필요하다.
 */
@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;

    /**
     * 좋아요 등록 - 멱등 동작 (요구사항 P-1)
     *
     * <ul>
     *   <li>상품이 없거나 삭제된 경우 → 404 (멱등 대상 아님, P-3)</li>
     *   <li>이미 좋아요한 경우 → 추가 작업 없이 정상 종료 (멱등)</li>
     *   <li>동시 요청으로 UK 위반이 발생한 경우 → 예외를 잡고 정상 종료 (멱등 최후 방어선)</li>
     * </ul>
     */
    @Transactional
    public void like(Long userId, Long productId) {
        productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND,
                "[id = " + productId + "] 상품을 찾을 수 없습니다."));

        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return;
        }

        try {
            // saveAndFlush: 즉시 flush로 UK 위반을 커밋 전에 이 try-catch 안에서 잡기 위함.
            // save()는 INSERT를 트랜잭션 커밋 시점까지 지연할 수 있어 catch를 우회할 위험이 있다.
            likeRepository.saveAndFlush(LikeModel.of(userId, productId));
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 UK 위반 - 다른 요청이 이미 좋아요를 등록 완료. 멱등성 보장.
        }
    }

    /**
     * 좋아요 취소 - 멱등 동작 (요구사항 P-2)
     *
     * <p>좋아요 레코드가 없어도 정상 종료한다. 삭제된 상품에 걸린 좋아요도 취소 가능.
     */
    @Transactional
    public void unlike(Long userId, Long productId) {
        likeRepository.delete(userId, productId);
    }

    /**
     * 내가 좋아요한 상품 목록.
     *
     * <p>좋아요 레코드 → 상품 IN 조회 → 재고/좋아요 수 IN 조회 → DTO 어셈블.
     * 모든 조회를 IN 쿼리 일괄 조회로 처리하여 N+1을 회피한다.
     */
    @Transactional(readOnly = true)
    public List<ProductInfo> getLikedProducts(Long userId) {
        List<LikeModel> likes = likeRepository.findByUserId(userId);
        if (likes.isEmpty()) {
            return List.of();
        }
        List<Long> productIds = likes.stream().map(LikeModel::getProductId).toList();
        List<ProductModel> products = productRepository.findAllByIds(productIds);
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> existingIds = products.stream().map(ProductModel::getId).toList();
        return ProductInfo.assembleUserList(
            products,
            stockRepository.findAllByProductIdIn(existingIds),
            likeRepository.countByProductIdIn(existingIds)
        );
    }

    /** 상품의 좋아요 수 집계. 다른 Facade(상품 상세/목록)에서 사용. */
    @Transactional(readOnly = true)
    public long countByProductId(Long productId) {
        return likeRepository.countByProductId(productId);
    }

    /**
     * 여러 상품의 좋아요 수 일괄 집계 (목록 N+1 회피용).
     *
     * @return {@code productId → count} 맵. 좋아요 0개인 상품은 맵에 없으므로
     *         호출 측에서 {@code getOrDefault(id, 0L)} 로 안전하게 조회한다.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> countByProductIdIn(List<Long> productIds) {
        return likeRepository.countByProductIdIn(productIds);
    }
}
