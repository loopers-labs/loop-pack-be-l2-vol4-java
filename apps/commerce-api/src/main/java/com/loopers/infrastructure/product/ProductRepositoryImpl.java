package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSearchCondition;
import com.loopers.domain.product.SortType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
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
    public Optional<ProductModel> findActiveById(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<ProductModel> findAllActiveByIds(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public Page<ProductModel> findAllActive(Pageable pageable, ProductSearchCondition condition) {
        Sort sort = toSort(condition.sortType());
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        if (condition.brandId() != null) {
            return productJpaRepository.findAllByBrand_IdAndDeletedAtIsNull(condition.brandId(), sorted);
        }
        return productJpaRepository.findAllByDeletedAtIsNull(sorted);
    }

    @Override
    public List<ProductModel> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrand_Id(brandId);
    }

    @Override
    public void softDeleteAllByBrandId(Long brandId) {
        productJpaRepository.softDeleteAllByBrandId(brandId, ZonedDateTime.now());
    }

    @Override
    public void incrementLikeCount(Long productId) {
        productJpaRepository.incrementLikeCount(productId);
    }

    @Override
    public void decrementLikeCount(Long productId) {
        productJpaRepository.decrementLikeCount(productId);
    }

    private Sort toSort(SortType sortType) {
        return switch (sortType) {
            case PRICE_ASC  -> Sort.by(Sort.Direction.ASC, "price");
            case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likeCount");
            default         -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }
}
