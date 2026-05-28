package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
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
    public Optional<ProductModel> findActive(UUID id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Page<ProductModel> findAll(Pageable pageable) {
        return productJpaRepository.findAll(pageable);
    }

    @Override
    public Page<ProductModel> findAllActive(Pageable pageable) {
        return productJpaRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Override
    public List<ProductModel> findAllByBrandId(UUID brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
    }
}
