package com.loopers.product.infrastructure;

import com.loopers.product.domain.ProductModel;
import com.loopers.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

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
