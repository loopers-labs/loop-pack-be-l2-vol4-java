package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    public List<ProductModel> findAllActive(Long brandId, ProductSortType sort, int page, int size) {
        return productJpaRepository.findActivePage(brandId, PageRequest.of(page, size, toSort(sort)));
    }

    @Override
    public Page<ProductModel> findAll(Long brandId, Pageable pageable) {
        Pageable latest = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), toSort(ProductSortType.LATEST));
        return productJpaRepository.findAllPage(brandId, latest);
    }

    @Override
    public List<ProductModel> findAllActiveByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
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
