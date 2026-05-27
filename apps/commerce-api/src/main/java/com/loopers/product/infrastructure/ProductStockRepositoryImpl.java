package com.loopers.product.infrastructure;

import com.loopers.product.domain.ProductStock;
import com.loopers.product.domain.ProductStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductStockRepositoryImpl implements ProductStockRepository {

    private final ProductStockJpaRepository productStockJpaRepository;

    @Override
    public ProductStock save(ProductStock stock) {
        return productStockJpaRepository.save(stock);
    }

    @Override
    public Optional<ProductStock> findByProductId(Long productId) {
        return productStockJpaRepository.findByProductIdAndDeletedAtIsNull(productId);
    }

    @Override
    public Optional<ProductStock> findByProductIdForUpdate(Long productId) {
        return productStockJpaRepository.findByProductIdForUpdate(productId);
    }

    @Override
    public int softDeleteByBrandId(Long brandId) {
        return productStockJpaRepository.softDeleteByBrandId(brandId);
    }
}
