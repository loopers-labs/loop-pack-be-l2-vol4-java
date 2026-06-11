package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class StockService {

    private final StockRepository stockRepository;

    public StockModel create(UUID productId, int initialQuantity) {
        return stockRepository.save(new StockModel(productId, initialQuantity));
    }

    public Map<UUID, StockModel> findAllByProductIds(List<UUID> productIds) {
        return stockRepository.findAllByProductIds(productIds).stream()
            .collect(java.util.stream.Collectors.toMap(StockModel::getProductId, s -> s));
    }

    public StockModel getByProductId(UUID productId) {
        return stockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고 정보를 찾을 수 없습니다."));
    }

    /**
     * 재고 예약 — 원자적 조건부 UPDATE.
     * affected rows = 0이면 재고 부족으로 CONFLICT.
     */
    public void reserve(UUID productId, int qty) {
        int affected = stockRepository.reserve(productId, qty);
        if (affected == 0) {
            throw new CoreException(ErrorType.CONFLICT, "재고가 부족합니다.");
        }
    }

    /** 결제 확정 — total--, reserved-- (비관적 락으로 lost update 방지) */
    public void confirm(UUID productId, int qty) {
        StockModel stock = stockRepository.findByProductIdForUpdate(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고 정보를 찾을 수 없습니다."));
        stock.confirm(qty);
    }

    /** 결제 실패/만료 — reserved만 해제 (비관적 락으로 lost update 방지) */
    public void release(UUID productId, int qty) {
        StockModel stock = stockRepository.findByProductIdForUpdate(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고 정보를 찾을 수 없습니다."));
        stock.release(qty);
    }

    /** 주문 취소(confirm 이후) — total 복구 (비관적 락으로 lost update 방지) */
    public void restore(UUID productId, int qty) {
        StockModel stock = stockRepository.findByProductIdForUpdate(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고 정보를 찾을 수 없습니다."));
        stock.restore(qty);
    }

    /**
     * 스케줄러 배치용 재고 해제 — productId별 합산 수량을 원자적 UPDATE.
     * SELECT FOR UPDATE 없이 단일 UPDATE per unique product.
     */
    public void releaseAll(Map<UUID, Integer> productQtyMap) {
        productQtyMap.forEach((productId, qty) -> {
            int affected = stockRepository.releaseByProductId(productId, qty);
            if (affected == 0) {
                log.error("재고 해제 실패 — 데이터 불일치 [productId={}, qty={}]", productId, qty);
            }
        });
    }

    /**
     * 어드민 재고 총량 수정 — 원자적 조건부 UPDATE.
     * reserved 미만이면 affected rows = 0 → CONFLICT.
     */
    public StockModel updateTotal(UUID productId, int newTotal) {
        int affected = stockRepository.updateTotal(productId, newTotal);
        if (affected == 0) {
            throw new CoreException(ErrorType.CONFLICT, "예약 중인 수량 이상으로만 재고를 설정할 수 있습니다.");
        }
        return getByProductId(productId);
    }
}
