package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
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
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<Product> find(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<Product> findAllByIds(List<Long> ids) {
        return productJpaRepository.findByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public List<Product> findAllForUpdate(List<Long> ids) {
        return productJpaRepository.findAllByIdInForUpdate(ids);
    }

    @Override
    public List<Product> findAll(Long brandId, ProductSortType sort, int page, int size) {
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
