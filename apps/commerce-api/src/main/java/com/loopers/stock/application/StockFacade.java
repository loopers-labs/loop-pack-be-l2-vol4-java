package com.loopers.stock.application;

import com.loopers.stock.domain.StockModel;
import com.loopers.stock.domain.StockRepository;
import com.loopers.stock.domain.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class StockFacade {

    private final StockService stockService;
    private final StockRepository stockRepository;

    @Transactional
    public StockInfo createStock(Long productId, Integer totalStock) {
        StockModel stock = new StockModel(productId, totalStock);
        return StockInfo.from(stockRepository.save(stock));
    }

    @Transactional(readOnly = true)
    public StockInfo getStock(Long productId) {
        StockModel stock = stockService.getOrThrow(stockRepository.findByProductId(productId));
        return StockInfo.from(stock);
    }
}
