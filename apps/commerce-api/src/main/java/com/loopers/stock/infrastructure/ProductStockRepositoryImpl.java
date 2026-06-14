package com.loopers.stock.infrastructure;

import com.loopers.stock.domain.ProductStock;
import com.loopers.stock.domain.ProductStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductStockRepositoryImpl implements ProductStockRepository {

    private final ProductStockJpaRepository productStockJpaRepository;

    @Override
    public ProductStock save(ProductStock productStock) {
        return productStockJpaRepository.save(productStock);
    }

    @Override
    public Optional<ProductStock> findByProductId(Long productId) {
        return productStockJpaRepository.findByProductId(productId);
    }

    @Override
    public List<ProductStock> findAllByProductIds(Collection<Long> productIds) {
        return productStockJpaRepository.findAllByProductIdIn(productIds);
    }

    @Override
    public List<ProductStock> findAllByProductIdsForUpdate(Collection<Long> productIds) {
        return productStockJpaRepository.findAllByProductIdInOrderByProductIdAsc(productIds);
    }
}
