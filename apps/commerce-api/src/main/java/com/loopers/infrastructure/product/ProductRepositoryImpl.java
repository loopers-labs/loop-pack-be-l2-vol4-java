package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product save(Product domain) {
        if (domain.getId() == null) {
            ProductEntity entity = new ProductEntity(domain.getBrandId(), domain.getName(), domain.getPrice());
            return productJpaRepository.save(entity).toDomain();
        }
        ProductEntity entity = productJpaRepository.findById(domain.getId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
        entity.updateFrom(domain);
        return productJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Product> find(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id)
            .map(ProductEntity::toDomain);
    }

    @Override
    public Page<Product> findAll(Long brandId, Pageable pageable) {
        if (brandId != null) {
            return productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId, pageable)
                .map(ProductEntity::toDomain);
        }
        return productJpaRepository.findAllByDeletedAtIsNull(pageable)
            .map(ProductEntity::toDomain);
    }

    @Override
    public void deleteAllByBrandId(Long brandId) {
        productJpaRepository.deleteAllByBrandId(brandId);
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
    public void adjustLikeCount(Long id, long amount) {
        productJpaRepository.adjustLikeCount(id, amount);
    }
}
