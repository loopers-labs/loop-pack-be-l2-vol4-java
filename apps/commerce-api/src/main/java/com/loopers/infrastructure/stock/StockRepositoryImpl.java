package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class StockRepositoryImpl implements StockRepository {

    private final StockJpaRepository stockJpaRepository;

    @Override
    public StockModel save(StockModel stock) {
        return stockJpaRepository.save(stock);
    }

    @Override
    public Optional<StockModel> findByProductId(Long productId) {
        return stockJpaRepository.findByProductId(productId);
    }
}
