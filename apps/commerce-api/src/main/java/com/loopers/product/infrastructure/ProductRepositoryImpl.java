package com.loopers.product.infrastructure;

import com.loopers.product.domain.Product;
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
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<Product> findAllOrderByLatest() {
        return productJpaRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc();
    }

    @Override
    public List<Product> findAllOrderByPriceAsc() {
        return productJpaRepository.findAllByDeletedAtIsNullOrderByPriceAsc();
    }

    @Override
    public int softDeleteByBrandId(Long brandId) {
        return productJpaRepository.softDeleteByBrandId(brandId);
    }
}
