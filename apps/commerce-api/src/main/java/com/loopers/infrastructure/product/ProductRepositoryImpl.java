package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductEntity;
import com.loopers.domain.product.ProductRepository;
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
    public ProductEntity save(ProductEntity product) {
        return ProductMapper.toDomain(productJpaRepository.save(ProductMapper.toJpaEntity(product)));
    }

    @Override
    public Optional<ProductEntity> find(String id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(ProductMapper::toDomain);
    }

    @Override
    public Page<ProductEntity> findAll(String brandId, Pageable pageable) {
        if (brandId != null) {
            return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId, pageable)
                    .map(ProductMapper::toDomain);
        }
        return productJpaRepository.findAllByDeletedAtIsNull(pageable)
                .map(ProductMapper::toDomain);
    }

    @Override
    public List<String> findIdsByBrandId(String brandId) {
        return productJpaRepository.findIdsByBrandId(brandId);
    }

    @Override
    public List<ProductEntity> findAllByIds(List<String> ids) {
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids).stream()
                .map(ProductMapper::toDomain)
                .toList();
    }

    @Override
    public void incrementLikeCount(String id) {
        productJpaRepository.incrementLikeCount(id);
    }

    @Override
    public void decrementLikeCount(String id) {
        productJpaRepository.decrementLikeCount(id);
    }
}
