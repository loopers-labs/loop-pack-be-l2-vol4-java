package com.loopers.application.stock;

import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class StockService {

    private final StockRepository stockRepository;

    @Transactional
    public StockModel create(StockModel stock) {
        return stockRepository.save(stock);
    }

    @Transactional(readOnly = true)
    public StockModel getByProductId(Long productId) {
        return stockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다."));
    }

    @Transactional
    public void decrease(Long productId, int qty) {
        StockModel stock = getByProductId(productId);
        stock.decrease(qty);
    }

    @Transactional
    public void increase(Long productId, int qty) {
        StockModel stock = getByProductId(productId);
        stock.increase(qty);
    }

    @Transactional
    public void update(Long productId, int quantity) {
        StockModel stock = getByProductId(productId);
        stock.update(quantity);
    }

    @Transactional
    public StockModel getByProductIdForUpdate(Long productId) {
        return stockRepository.findByProductIdForUpdate(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public Optional<StockModel> findByProductId(Long productId) {
        return stockRepository.findByProductId(productId);
    }
}
