package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.ProductStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProductStockRepositoryImpl implements ProductStockRepository {

    private final ProductStockJpaRepository productStockJpaRepository;

    @Override
    public ProductStockModel save(ProductStockModel stock) {
        return productStockJpaRepository.save(stock);
    }

    @Override
    public Optional<ProductStockModel> findByProductId(Long productId) {
        return productStockJpaRepository.findByProductIdAndDeletedAtIsNull(productId);
    }
}