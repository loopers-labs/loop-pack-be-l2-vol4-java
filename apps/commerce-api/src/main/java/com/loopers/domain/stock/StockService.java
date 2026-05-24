package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;

    @Transactional
    public StockModel create(Long productId, Integer quantity) {
        StockModel stock = new StockModel(productId, quantity);
        return stockRepository.save(stock);
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
