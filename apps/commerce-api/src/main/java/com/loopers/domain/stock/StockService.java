package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 재고 도메인 서비스 — 상품별 재고의 초기화/차감/복원/조회를 담당한다.
 * 차감·복원은 동시 주문에서의 정합성을 위해 비관적 락(SELECT ... FOR UPDATE)을 기본 경로로 쓴다.
 * 낙관적 락(@Version) 경로(decreaseOptimistic)도 함께 제공해 전략을 비교할 수 있게 한다(쿠폰과 일관).
 */
@RequiredArgsConstructor
@Component
public class StockService {

    private final StockRepository stockRepository;

    /** 재고 초기화 (상품 등록 시 — ProductFacade가 같은 트랜잭션으로 조정). */
    @Transactional
    public StockModel initialize(Long productId, int quantity) {
        return stockRepository.save(new StockModel(productId, quantity));
    }

    /**
     * 재고 차감 — 비관적 락(기본). 행을 잠그고 차감해 동시 주문을 직렬화한다(lost update 방지).
     * 재고 부족 시 CONFLICT, 재고 행 부재 시 NOT_FOUND → 주문 트랜잭션 전체 롤백.
     */
    @Transactional
    public void decrease(Long productId, int quantity) {
        StockModel stock = stockRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다."));
        stock.deduct(quantity);
        stockRepository.save(stock);
    }

    /**
     * 재고 차감 — 낙관적 락(@Version) 경로. 동시 차감 시 version 충돌로 예외가 발생해
     * 주문 트랜잭션이 롤백된다(비관 경로와 비교용 — UC-20 §5-A 대응).
     */
    @Transactional
    public void decreaseOptimistic(Long productId, int quantity) {
        StockModel stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다."));
        stock.deduct(quantity);
        stockRepository.save(stock);
    }

    /** 재고 복원 (결제 실패 원복 — 01 §7.6). 비관적 락으로 차감과 동일 직렬화. */
    @Transactional
    public void increase(Long productId, int quantity) {
        StockModel stock = stockRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다."));
        stock.restore(quantity);
        stockRepository.save(stock);
    }

    /**
     * 재고를 절대값으로 조정 (Admin 상품 수정 — UC-11). 행을 잠그고 새 수량으로 교체한다.
     * 재고 행 부재 시 NOT_FOUND (상품 등록 시 initialize로 항상 생성됨).
     */
    @Transactional
    public void adjust(Long productId, int quantity) {
        StockModel stock = stockRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다."));
        stock.changeQuantity(quantity);
        stockRepository.save(stock);
    }

    /** 단건 재고 수량 — 상품 상세 inStock 조합용. 재고 행 부재면 0. */
    @Transactional(readOnly = true)
    public int getQuantity(Long productId) {
        return stockRepository.findByProductId(productId)
                .map(StockModel::getQuantity)
                .orElse(0);
    }

    /** 여러 상품의 재고 수량 batch — 상품 목록 inStock 조합 N+1 회피. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> findQuantities(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return stockRepository.findByProductIds(productIds).stream()
                .collect(Collectors.toMap(StockModel::getProductId, StockModel::getQuantity, (a, b) -> a));
    }
}
