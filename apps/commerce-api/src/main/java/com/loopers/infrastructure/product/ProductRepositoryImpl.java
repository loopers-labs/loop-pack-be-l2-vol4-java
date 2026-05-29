package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.SortType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        Sort sort = switch (sortType) {
            case LATEST     -> Sort.by(Sort.Direction.DESC, "createdAt");
            case PRICE_ASC  -> Sort.by(Sort.Direction.ASC, "price");
            case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likeCount");
        };
        return productJpaRepository.findAllByDeletedAtIsNull(sort);
    }

    @Override
    public void delete(Long id) {
        productJpaRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void increaseLikeCount(Long productId) {
        productJpaRepository.increaseLikeCount(productId);
    }

    @Override
    @Transactional
    public void decreaseLikeCount(Long productId) {
        productJpaRepository.decreaseLikeCount(productId);
    }

    @Override
    @Transactional
    public void deleteAllByBrandId(Long brandId) {
        productJpaRepository.softDeleteAllByBrandId(brandId, java.time.ZonedDateTime.now());
    }
}
