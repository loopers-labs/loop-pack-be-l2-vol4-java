package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;

    @Transactional
    public StockModel create(Long productId, Integer quantity) {
        StockModel stock = new StockModel(productId, quantity);
        return stockRepository.save(stock);
    }

    @Transactional
    public void decrease(Long productId, int amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.INVALID_QUANTITY, "차감 수량은 1 이상이어야 합니다.");
        }
        StockModel stock = stockRepository.findByProductIdForUpdate(productId)
            .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다."));
        stock.decrease(amount);
    }

    /**
     * 여러 상품의 재고를 한 번에 차감한다. productId 오름차순으로 잠금을 획득해
     * 다항목 주문 간 락 순서가 엇갈려 발생하는 데드락을 회피한다.
     */
    @Transactional
    public void decreaseAll(Map<Long, Integer> quantitiesByProductId) {
        quantitiesByProductId.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> decrease(entry.getKey(), entry.getValue()));
    }

    @Transactional(readOnly = true)
    public StockModel getByProductId(Long productId) {
        return stockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<StockModel> getAllByProductIdIn(Collection<Long> productIds) {
        return stockRepository.findAllByProductIdIn(productIds);
    }

    @Transactional
    public void deleteByProductId(Long productId) {
        stockRepository.deleteByProductId(productId);
    }
}
