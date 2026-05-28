package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortOption;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    public List<ProductModel> findAll(Long brandId, ProductSortOption sort, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, toJpaSort(sort));
        if (brandId == null) {
            return productJpaRepository.findAll(pageable).getContent();
        }
        return productJpaRepository.findByBrandId(brandId, pageable).getContent();
    }

    private Sort toJpaSort(ProductSortOption option) {
        return switch (option) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price.amount");
            case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likeCount");
        };
    }
}
