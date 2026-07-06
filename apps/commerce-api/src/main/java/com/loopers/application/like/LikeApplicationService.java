package com.loopers.application.like;

import com.loopers.application.activity.UserActionEvent;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

/**
 * 좋아요 유스케이스 Application Service.
 *
 * <p>스타일 2 (DDD 정통): Application Layer 가 조회·검증·저장을 책임진다.
 *
 * <p><strong>좋아요 수 비정규화 + 집계 분리 (Round 5→7)</strong>: 좋아요 수는 likes 집계 대신
 * {@code products.like_count} 컬럼으로 유지한다. 등록/취소가 <strong>likes 테이블을 실제로 변경한 경우에만</strong>
 * {@link LikeChangedEvent}를 발행하고, like_count 증감은 커밋 이후 {@link LikeCountEventListener}가
 * 비동기(별도 트랜잭션)로 반영한다 — 집계 실패와 무관하게 좋아요는 성공한다(최종 일관성).
 * (이미 좋아요/이미 없음/동시요청 UK위반 → likes 무변경 → 이벤트 미발행 → count 무변경)
 */
@RequiredArgsConstructor
@Service
public class LikeApplicationService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 좋아요 등록 - 멱등 동작 (요구사항 P-1)
     *
     * <ul>
     *   <li>상품이 없으면 → 404 (멱등 대상 아님, P-3)</li>
     *   <li>이미 좋아요한 경우 → 추가 작업 없이 정상 종료 (count 갱신 안 함)</li>
     *   <li>동시 요청 UK 위반 → 예외를 잡고 정상 종료 (이벤트 미발행)</li>
     *   <li>INSERT 가 실제 성공한 경우에만 {@link LikeChangedEvent} 발행 → 커밋 후 like_count + 1</li>
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
            // saveAndFlush: 즉시 flush 로 UK 위반을 커밋 전 이 try-catch 안에서 잡는다.
            likeRepository.saveAndFlush(LikeModel.of(userId, productId));
            // INSERT 가 실제 성공한 경로에서만 집계 이벤트 발행 (like_count 증가는 커밋 후 리스너가 반영)
            eventPublisher.publishEvent(LikeChangedEvent.liked(productId));
            // 유저 행동 로깅 (커밋 후 리스너가 기록)
            eventPublisher.publishEvent(UserActionEvent.like(userId, productId));
        } catch (DataIntegrityViolationException e) {
            if (!isDuplicateKeyViolation(e)) {
                throw e;
            }
            // UK 위반(MySQL 1062) = 동시 요청으로 이미 등록됨. likes 무변경이므로 count 도 건드리지 않음.
        }
    }

    private static boolean isDuplicateKeyViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof SQLIntegrityConstraintViolationException sqlEx
                && sqlEx.getErrorCode() == 1062) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * 좋아요 취소 - 멱등 동작 (요구사항 P-2)
     *
     * <p>실제 삭제된 행이 있을 때만 {@link LikeChangedEvent} 발행 → 커밋 후 like_count - 1.
     * 원래 없던 좋아요를 취소하면 이벤트 미발행 → count 무변경.
     */
    @Transactional
    public void unlike(Long userId, Long productId) {
        int deleted = likeRepository.delete(userId, productId);
        if (deleted > 0) {
            // 실제 삭제된 경우에만 집계 이벤트 발행 (like_count 감소는 커밋 후 리스너가 반영)
            eventPublisher.publishEvent(LikeChangedEvent.unliked(productId));
            // 유저 행동 로깅 (커밋 후 리스너가 기록)
            eventPublisher.publishEvent(UserActionEvent.unlike(userId, productId));
        }
    }

    /**
     * 내가 좋아요한 상품 목록.
     *
     * <p>좋아요 수는 비정규화 컬럼(like_count)을 그대로 사용하므로 별도 집계 쿼리가 없다.
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
            stockRepository.findAllByProductIdIn(existingIds)
        );
    }
}
