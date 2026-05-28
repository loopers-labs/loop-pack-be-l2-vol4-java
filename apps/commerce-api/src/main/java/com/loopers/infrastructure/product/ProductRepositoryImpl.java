package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
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
    public ProductModel save(ProductModel product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<ProductModel> find(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<ProductModel> findWithLock(Long id) {
        return productJpaRepository.findByIdWithLock(id);
    }

    @Override
    public List<ProductModel> findAll() {
        return productJpaRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public List<ProductModel> findAllActive(Long brandId) {
        if (brandId != null) {
            return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
        }
        return productJpaRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public void delete(Long id) {
        productJpaRepository.findByIdAndDeletedAtIsNull(id).ifPresent(product -> {
            product.delete();
            productJpaRepository.save(product);
        });
    }
}