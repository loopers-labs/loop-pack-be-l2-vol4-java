package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import lombok.RequiredArgsConstructor;
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
    public List<ProductModel> findAll() {
        return productJpaRepository.findAll();
    }

    @Override
    public List<ProductModel> findAll(ProductSort sort) {
        return productJpaRepository.findAll(toJpaSort(sort));
    }

    @Override
    public void delete(Long id) {
        productJpaRepository.deleteById(id);
    }

    private Sort toJpaSort(ProductSort sort) {
        return switch (sort) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price");
            case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likeCount");
        };
    }
}
