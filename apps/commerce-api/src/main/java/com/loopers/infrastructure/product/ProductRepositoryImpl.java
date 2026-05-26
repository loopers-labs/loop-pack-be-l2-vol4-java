package com.loopers.infrastructure.product;

import org.springframework.stereotype.Component;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public ProductModel save(ProductModel product) {
        return productJpaRepository.save(product);
    }
}
