package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class StockService {

    private final StockRepository stockRepository;

    public StockModel getByProductId(Long productId) {
        return stockRepository.findByProductId(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 재고를 찾을 수 없습니다."));
    }

    public StockModel create(Long productId, Long quantity) {
        return stockRepository.save(new StockModel(productId, quantity));
    }

    public StockModel update(Long productId, Long quantity) {
        StockModel stock = getByProductId(productId);
        stock.update(quantity);
        return stockRepository.save(stock);
    }

    public void delete(Long productId) {
        StockModel stock = getByProductId(productId);
        stock.delete();
        stockRepository.save(stock);
    }
}
