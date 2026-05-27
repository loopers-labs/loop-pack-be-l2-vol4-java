package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.SortType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
        return productJpaRepository.findById(id);
    }

    @Override
    public List<ProductModel> findAll(SortType sortType) {
        return switch (sortType) {
            case LATEST -> productJpaRepository.findAllByOrderByCreatedAtDesc();
            case PRICE_ASC -> productJpaRepository.findAllByOrderByPriceAsc();
            case LIKES_DESC -> productJpaRepository.findAllByOrderByLikeCountDesc();
        };
    }

    @Override
    public List<ProductModel> findAllByIds(List<Long> ids) {
        return productJpaRepository.findAllByIdIn(ids);
    }

    @Override
    public List<ProductModel> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandId(brandId);
    }

    @Override
    public Page<ProductModel> findAllByBrandId(Long brandId, Pageable pageable) {
        return productJpaRepository.findAllByBrandId(brandId, pageable);
    }

    @Override
    public void increaseLikeCount(Long id) {
        productJpaRepository.increaseLikeCount(id);
    }

    @Override
    public void decreaseLikeCount(Long id) {
        productJpaRepository.decreaseLikeCount(id);
    }
}
