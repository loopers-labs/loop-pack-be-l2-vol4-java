package com.loopers.infrastructure.catalog.product;

import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.catalog.product.ProductSearchCondition;
import com.loopers.domain.catalog.product.ProductSortType;
import com.loopers.domain.catalog.product.ProductStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product save(Product product) {
        ProductJpaEntity entity = product.isNew()
            ? ProductJpaEntity.from(product)
            : productJpaRepository.findById(product.getId()).orElseGet(() -> ProductJpaEntity.from(product));
        entity.apply(product);
        return productJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Product> find(Long id) {
        return productJpaRepository.findById(id).map(ProductJpaEntity::toDomain);
    }

    @Override
    public Optional<Product> findOnSale(Long id) {
        return productJpaRepository.findByIdAndStatus(id, ProductStatus.ON_SALE).map(ProductJpaEntity::toDomain);
    }

    @Override
    public List<Product> findAllByIds(Collection<Long> ids) {
        return productJpaRepository.findAllById(ids)
            .stream()
            .map(ProductJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<Product> findAllByIdsForUpdate(Collection<Long> ids) {
        return productJpaRepository.findAllByIdInForUpdate(ids)
            .stream()
            .map(ProductJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<Product> findByBrandId(Long brandId) {
        return productJpaRepository.findByBrandId(brandId)
            .stream()
            .map(ProductJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<Product> search(ProductSearchCondition condition) {
        PageRequest pageRequest = PageRequest.of(condition.page(), condition.size(), toSort(condition.sort()));
        return searchEntities(condition, pageRequest)
            .stream()
            .map(ProductJpaEntity::toDomain)
            .toList();
    }

    @Override
    public long count(ProductSearchCondition condition) {
        if (condition.status() != null && condition.brandId() != null) {
            return productJpaRepository.countByStatusAndBrandId(condition.status(), condition.brandId());
        }
        if (condition.status() != null) {
            return productJpaRepository.countByStatus(condition.status());
        }
        return productJpaRepository.countByOptionalStatusAndBrandId(condition.status(), condition.brandId());
    }

    @Override
    public int increaseLikeCount(Long productId) {
        return productJpaRepository.increaseLikeCount(productId);
    }

    @Override
    public int decreaseLikeCount(Long productId) {
        return productJpaRepository.decreaseLikeCount(productId);
    }

    private Sort toSort(ProductSortType sort) {
        return switch (sort) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price");
            case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likeCount");
        };
    }

    private List<ProductJpaEntity> searchEntities(ProductSearchCondition condition, PageRequest pageRequest) {
        if (condition.status() != null && condition.brandId() != null) {
            return productJpaRepository.findByStatusAndBrandId(condition.status(), condition.brandId(), pageRequest);
        }
        if (condition.status() != null) {
            return productJpaRepository.findByStatus(condition.status(), pageRequest);
        }
        return productJpaRepository.findByOptionalStatusAndBrandId(condition.status(), condition.brandId(), pageRequest);
    }
}
