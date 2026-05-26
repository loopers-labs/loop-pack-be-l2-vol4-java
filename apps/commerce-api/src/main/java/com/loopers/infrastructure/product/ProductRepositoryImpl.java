package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductJpaRepository productJpaRepository;

    @Override
    public ProductModel save(ProductModel product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<ProductModel> find(UUID id) {
        return productJpaRepository.findById(id);
    }

    @Override
    public List<ProductModel> findAll() {
        return productJpaRepository.findAll();
    }

    @Override
    public void delete(UUID id) {
        productJpaRepository.deleteById(id);
    }
}
