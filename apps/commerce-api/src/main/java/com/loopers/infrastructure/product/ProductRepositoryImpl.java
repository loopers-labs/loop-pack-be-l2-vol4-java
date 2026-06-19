package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
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
    public Product save(Product product) {
        ProductJpaEntity productJpaEntity = product.getId() == null
            ? ProductJpaEntity.from(product)
            : productJpaRepository.findById(product.getId())
                .map(existingProduct -> {
                    existingProduct.update(product);
                    return existingProduct;
                })
                .orElseGet(() -> ProductJpaEntity.from(product));

        return productJpaRepository.save(productJpaEntity).toDomain();
    }

    @Override
    public Optional<Product> find(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id)
            .map(ProductJpaEntity::toDomain);
    }

    @Override
    public List<Product> findAllByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids).stream()
            .map(ProductJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<Product> findAllByIdsForUpdate(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return productJpaRepository.findAllByIdInAndDeletedAtIsNullForUpdate(ids).stream()
            .map(ProductJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<Product> findAll(ProductSort sort, int page, int size) {
        return productJpaRepository.findAllByDeletedAtIsNull(PageRequest.of(page, size, toJpaSort(sort))).stream()
            .map(ProductJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<Product> findAllByBrandId(Long brandId) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId).stream()
            .map(ProductJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<Product> findAllByBrandId(Long brandId, ProductSort sort, int page, int size) {
        return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(
            brandId,
            PageRequest.of(page, size, toJpaSort(sort))
        ).stream()
            .map(ProductJpaEntity::toDomain)
            .toList();
    }

    private Sort toJpaSort(ProductSort sort) {
        return switch (sort) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price");
            case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likeCount");
        };
    }
}
