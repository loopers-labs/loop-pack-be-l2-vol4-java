package com.loopers.stock.infrastructure;

import com.loopers.stock.domain.StockModel;
import com.loopers.stock.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
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

    @Override
    public List<StockModel> findAllByProductIds(List<Long> productIds) {
        if (productIds.isEmpty()) return List.of();
        return stockJpaRepository.findAllByProductIds(productIds);
    }

    @Override
    public List<StockModel> findAllByProductIdsWithLock(List<Long> productIds) {
        if (productIds.isEmpty()) return List.of();
        return stockJpaRepository.findAllByProductIdsForUpdate(productIds);
    }
}
