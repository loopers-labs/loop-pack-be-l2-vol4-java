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
        return productJpaRepository.findById(id).map(ProductMapper::toDomain);
    }

    @Override
    public List<ProductEntity> findAll() {
        return productJpaRepository.findAll().stream()
                .map(ProductMapper::toDomain)
                .toList();
    }

    @Override
    public void delete(Long id) {
        productJpaRepository.deleteById(id);
    }
}
