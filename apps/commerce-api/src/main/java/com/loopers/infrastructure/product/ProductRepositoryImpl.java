package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
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
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<ProductModel> findAll() {
        return findAll(ProductSort.LATEST);
    }

    @Override
    public List<ProductModel> findAll(ProductSort sort) {
        return productJpaRepository.findAllByDeletedAtIsNull(toJpaSort(sort));
    }

    @Override
    public List<ProductModel> findAll(ProductSort sort, int page, int size) {
        return productJpaRepository.findAllByDeletedAtIsNull(PageRequest.of(page, size, toJpaSort(sort)));
    }

    @Override
    public List<ProductModel> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public List<ProductModel> findAllByBrandId(Long brandId, ProductSort sort) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId, toJpaSort(sort));
    }

    @Override
    public List<ProductModel> findAllByBrandId(Long brandId, ProductSort sort, int page, int size) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(
            brandId,
            PageRequest.of(page, size, toJpaSort(sort))
        );
    }

    private Sort toJpaSort(ProductSort sort) {
        return switch (sort) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price");
            case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likeCount");
        };
    }
}
