package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    public Optional<ProductModel> findById(Long id) {
        return productJpaRepository.findById(id);
    }

    @Override
    public Optional<ProductModel> findByIdForUpdate(Long id) {
        return productJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public Page<ProductModel> findAll(Long brandId, ProductSort sort, PageRequest pageRequest) {
        PageRequest sortedPage = PageRequest.of(
            pageRequest.getPageNumber(),
            pageRequest.getPageSize(),
            toSort(sort)
        );

        if (brandId != null) {
            return productJpaRepository.findAllByBrandId(brandId, sortedPage);
        }
        return productJpaRepository.findAll(sortedPage);
    }

    @Override
    public List<ProductModel> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandId(brandId);
    }

    private Sort toSort(ProductSort sort) {
        return switch (sort) {
            case PRICE_ASC -> Sort.by(Sort.Order.asc("price"));
            case LIKES_DESC -> Sort.by(Sort.Order.desc("likeCount"));
            case LATEST -> Sort.by(Sort.Order.desc("createdAt"));
        };
    }
}
