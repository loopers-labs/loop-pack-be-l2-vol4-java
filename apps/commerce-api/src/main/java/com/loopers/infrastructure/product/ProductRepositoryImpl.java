package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    public Optional<ProductModel> findActive(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<ProductModel> findAll(ProductSortType sort, int page, int size) {
        return productJpaRepository.findAll(PageRequest.of(page, size, toSort(sort))).getContent();
    }

    @Override
    public List<ProductModel> findAllActiveByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public void delete(Long id) {
        productJpaRepository.deleteById(id);
    }

    @Override
    public int incrementLikeCount(Long id) {
        return productJpaRepository.incrementLikeCount(id);
    }

    @Override
    public int decrementLikeCount(Long id) {
        return productJpaRepository.decrementLikeCount(id);
    }

    private Sort toSort(ProductSortType sort) {
        return switch (sort) {
            case LATEST     -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
            case PRICE_ASC  -> Sort.by(Sort.Order.asc("price"),      Sort.Order.asc("id"));
            case LIKES_DESC -> Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("id"));
        };
    }
}
