package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.model.Stock;
import com.loopers.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
    public Stock save(Stock stock) {
        return stockJpaRepository.save(stock);
    }
}
