package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<ProductModel> findAllForUpdate(List<Long> ids) {
        return productJpaRepository.findAllByIdInForUpdate(ids);
    }

    @Override
    public List<ProductModel> findAll(Long brandId, ProductSortType sort, int page, int size) {
        ProductSortType sortType = (sort != null) ? sort : ProductSortType.LATEST;
        if (sortType == ProductSortType.LIKES_DESC) {
            Pageable pageable = PageRequest.of(page, size);
            if (brandId != null) {
                return productJpaRepository.findByBrandIdOrderByLikesDesc(brandId, pageable).getContent();
            }
            return productJpaRepository.findAllOrderByLikesDesc(pageable).getContent();
        }
        Pageable pageable = PageRequest.of(page, size, sortType.toSort());
        if (brandId != null) {
            return productJpaRepository.findByBrandIdAndDeletedAtIsNull(brandId, pageable).getContent();
        }
        return productJpaRepository.findByDeletedAtIsNull(pageable).getContent();
    }
}
