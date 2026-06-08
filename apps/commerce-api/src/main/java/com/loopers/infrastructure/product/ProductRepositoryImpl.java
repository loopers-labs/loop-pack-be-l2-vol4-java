package com.loopers.infrastructure.product;

import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findById(id);
    }

    @Override
    public List<Product> findAllByIdIn(List<Long> ids) {
        return productJpaRepository.findAllById(ids);
    }

    @Override
    public Page<Product> findAll(Long brandId, Pageable pageable) {
        return productJpaRepository.findAllByBrandIdFilter(brandId, pageable);
    }

    @Override
    public List<Long> findIdsByBrandId(Long brandId) {
        return productJpaRepository.findIdsByBrandId(brandId);
    }

    @Override
    public int softDeleteAllByBrandId(Long brandId) {
        return productJpaRepository.softDeleteAllByBrandId(brandId, ZonedDateTime.now());
    }

    @Override
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public int incrementLikeCount(Long productId) {
        return productJpaRepository.incrementLikeCount(productId);
    }

    @Override
    public int decrementLikeCount(Long productId) {
        return productJpaRepository.decrementLikeCount(productId);
    }
}
