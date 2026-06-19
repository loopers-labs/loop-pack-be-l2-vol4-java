package com.loopers.domain.stock;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class StockService {

    private final StockRepository stockRepository;

    public Map<Long, StockModel> getMapByProductIds(Collection<Long> productIds) {
        return stockRepository.findAllByProductIds(productIds).stream()
                .collect(Collectors.toMap(StockModel::getProductId, s -> s));
    }

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

    public void softDeleteAllByProductIds(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return;
        }
        stockRepository.softDeleteAllByProductIdIn(productIds, ZonedDateTime.now());
    }

    @Transactional
    public void decreaseStock(Long productId, Long quantity) {
        int updated = stockRepository.decreaseQuantity(productId, quantity);
        if (updated == 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "[productId = " + productId + "] 재고가 부족합니다.");
        }
    }
}
