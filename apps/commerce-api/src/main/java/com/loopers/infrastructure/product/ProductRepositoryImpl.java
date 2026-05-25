package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<Product> find(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Page<Product> findAll(Long brandId, Pageable pageable) {
        if (brandId != null) {
            return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId, pageable);
        }
        return productJpaRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Override
    public void deleteAllByBrandId(Long brandId) {
        productJpaRepository.deleteAllByBrandId(brandId);
    }
}
