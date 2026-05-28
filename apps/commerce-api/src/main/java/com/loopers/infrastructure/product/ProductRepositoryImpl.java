package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductEntity;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public ProductEntity save(ProductEntity product) {
        return ProductMapper.toDomain(productJpaRepository.save(ProductMapper.toJpaEntity(product)));
    }

    @Override
    public Optional<ProductEntity> find(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(ProductMapper::toDomain);
    }

    @Override
    public List<ProductEntity> findAll() {
        return productJpaRepository.findAllByDeletedAtIsNull().stream()
                .map(ProductMapper::toDomain)
                .toList();
    }
}
