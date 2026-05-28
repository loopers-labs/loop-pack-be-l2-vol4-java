package com.loopers.domain.stock.service;

import com.loopers.domain.stock.model.Stock;
import com.loopers.domain.stock.repository.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class StockDomainService {

    private final StockRepository stockRepository;

    public Stock getStock(Long productId) {
        return stockRepository.findByProductId(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "재고 정보를 찾을 수 없습니다."));
    }

    public Stock createStock(Long productId, int initialQuantity) {
        Stock stock = Stock.create(productId, initialQuantity);
        return stockRepository.save(stock);
    }
}
