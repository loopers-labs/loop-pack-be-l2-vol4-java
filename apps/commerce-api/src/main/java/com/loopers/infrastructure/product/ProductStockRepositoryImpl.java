package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductStock;
import com.loopers.domain.product.ProductStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductStockRepositoryImpl implements ProductStockRepository {

    private final ProductStockJpaRepository productStockJpaRepository;

    @Override
    public ProductStock save(ProductStock domain) {
        return productStockJpaRepository.save(ProductStockEntity.from(domain)).toDomain();
    }

    @Override
    public Optional<ProductStock> findByProductId(Long productId) {
        return productStockJpaRepository.findByProductId(productId)
            .map(ProductStockEntity::toDomain);
    }
}
