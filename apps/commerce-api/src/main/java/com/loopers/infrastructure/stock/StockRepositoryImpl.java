package com.loopers.infrastructure.stock;

import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class StockRepositoryImpl implements StockRepository {

    private final StockJpaRepository stockJpaRepository;

    @Override
    public StockModel save(StockModel stock) {
        return stockJpaRepository.save(stock);
    }

    @Override
    public Optional<StockModel> findByProductId(UUID productId) {
        return stockJpaRepository.findByProductId(productId);
    }

    @Override
    public List<StockModel> findAllByProductIds(List<UUID> productIds) {
        return stockJpaRepository.findAllByProductIdIn(productIds);
    }

    @Override
    public Optional<StockModel> findByProductIdForUpdate(UUID productId) {
        return stockJpaRepository.findByProductIdForUpdate(productId);
    }

    @Override
    public int reserve(UUID productId, int qty) {
        return stockJpaRepository.reserve(productId, qty);
    }

    @Override
    public int updateTotal(UUID productId, int newTotal) {
        return stockJpaRepository.updateTotal(productId, newTotal);
    }

    @Override
    public int releaseByProductId(UUID productId, int qty) {
        return stockJpaRepository.releaseByProductId(productId, qty);
    }
}
