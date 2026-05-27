package com.loopers.domain.stock.service;

import com.loopers.domain.stock.model.Stock;
import com.loopers.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class StockDomainService {

    private final StockRepository stockRepository;

    public Stock createStock(Long productId, int initialQuantity) {
        Stock stock = Stock.create(productId, initialQuantity);
        return stockRepository.save(stock);
    }
}
