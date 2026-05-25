package com.loopers.infrastructure.product;

import com.loopers.domain.product.model.ProductModel;
import com.loopers.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductJpaRepository productJpaRepository;

    @Override
    public ProductModel save(ProductModel product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<ProductModel> find(Long id) {
        return productJpaRepository.findById(id);
    }

    @Override
    public List<ProductModel> findAll() {
        return productJpaRepository.findAll();
    }

    @Override
    public void delete(Long id) {
        productJpaRepository.deleteById(id);
    }
}
