package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.model.Stock;
import com.loopers.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class StockRepositoryImpl implements StockRepository {

    private final StockJpaRepository stockJpaRepository;

    @Override
    public Optional<Stock> findByProductId(Long productId) {
        return stockJpaRepository.findByProductId(productId);
    }

    @Override
    public List<Stock> findAllByProductIdIn(List<Long> productIds) {
        return stockJpaRepository.findAllByProductIdIn(productIds);
    }

    @Override
    public int softDeleteAllByProductIdIn(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return 0;
        }
        return stockJpaRepository.softDeleteAllByProductIdIn(productIds, ZonedDateTime.now());
    }

    @Override
    public Stock save(Stock stock) {
        return stockJpaRepository.save(stock);
    }
}
