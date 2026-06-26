package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;
    private final BrandJpaRepository brandJpaRepository;

    @Override
    public ProductModel save(ProductModel product) {
        if (product.getId() != null) {
            ProductEntity entity = productJpaRepository.findById(product.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + product.getId() + "] 상품을 찾을 수 없습니다."));
            entity.update(product.getName(), product.getDescription(), product.getPrice(), product.getStock());
            return productJpaRepository.save(entity).toDomain();
        }
        var brand = brandJpaRepository.findById(product.getBrandId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + product.getBrandId() + "] 브랜드를 찾을 수 없습니다."));
        return productJpaRepository.save(ProductEntity.from(product, brand)).toDomain();
    }

    @Override
    public Optional<ProductModel> find(Long id) {
        return productJpaRepository.findById(id).map(ProductEntity::toDomain);
    }

    @Override
    public Optional<ProductModel> findWithLock(Long id) {
        return productJpaRepository.findByIdWithLock(id).map(ProductEntity::toDomain);
    }

    @Override
    public List<ProductModel> findAll(Long brandId, Pageable pageable) {
        return productJpaRepository.findAllByBrandId(brandId, pageable)
            .map(ProductEntity::toDomain).toList();
    }

    @Override
    public List<ProductModel> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return productJpaRepository.findAllById(ids).stream()
            .map(ProductEntity::toDomain)
            .toList();
    }

    @Override
    public List<ProductModel> findAllOrderByLikeCountDesc(Long brandId, Pageable pageable) {
        PageRequest pageRequest = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return productJpaRepository.findAllOrderByLikeCountDesc(brandId, pageRequest)
            .map(ProductEntity::toDomain).toList();
    }

    @Override
    public void incrementLikeCount(Long id) {
        productJpaRepository.incrementLikeCount(id);
    }

    @Override
    public void decrementLikeCount(Long id) {
        productJpaRepository.decrementLikeCount(id);
    }

    @Override
    public void delete(Long id) {
        productJpaRepository.deleteById(id);
    }

    @Override
    public void deleteByBrandId(Long brandId) {
        productJpaRepository.deleteByBrandId(brandId);
    }
}
