package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class StockService {

    private final StockRepository stockRepository;

    @Transactional
    public void decrease(Long productId, int amount) {
        doDecrease(productId, amount);
    }

    /** productId 오름차순 처리 — 동시 호출에서 락 순서를 통일해 데드락을 회피한다. */
    @Transactional
    public void decreaseAll(Map<Long, Integer> quantitiesByProductId) {
        quantitiesByProductId.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> doDecrease(entry.getKey(), entry.getValue()));
    }

    /** 운영 보충/보상 경로. decrease와 동일 행을 다루므로 lost update 방지를 위해 차감과 같은 FOR UPDATE 경로 사용. */
    @Transactional
    public void increase(Long productId, int amount) {
        StockModel stock = stockRepository.findByProductIdForUpdate(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다."));
        stock.increase(amount);
    }

    @Transactional(readOnly = true)
    public StockModel getByProductId(Long productId) {
        return stockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> getQuantities(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, Integer> result = new HashMap<>();
        for (StockModel stock : stockRepository.findAllByProductIdIn(productIds)) {
            result.put(stock.getProductId(), stock.getQuantity());
        }
        return result;
    }

    private void doDecrease(Long productId, int amount) {
        StockModel stock = stockRepository.findByProductIdForUpdate(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다."));
        stock.decrease(amount);
    }
}
