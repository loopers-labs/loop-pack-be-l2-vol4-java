package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
        return productJpaRepository.findById(id);
    }

    @Override
    public List<Product> findAll() {
        return productJpaRepository.findAll();
    }

    @Override
    public List<Product> findAll(Long brandId, ProductSortType sort, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, toSort(sort));
        if (brandId != null) {
            return productJpaRepository.findAllByBrandId(brandId, pageable).getContent();
        }
        return productJpaRepository.findAll(pageable).getContent();
    }

    @Override
    public List<Product> findAllByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return productJpaRepository.findAllByIdIn(ids);
    }

    @Override
    public boolean existsById(Long id) {
        return id != null && productJpaRepository.existsById(id);
    }

    @Override
    public void delete(Long id) {
        productJpaRepository.deleteById(id);
    }

    @Override
    public void deleteByBrandId(Long brandId) {
        productJpaRepository.deleteByBrandId(brandId);
    }

    /**
     * LATEST / PRICE_ASC 는 저장소에서 정렬한다.
     * LIKES_DESC 는 좋아요 집계가 필요하므로 여기서는 최신순으로 가져오고, 조합 단계에서 재정렬한다.
     */
    private Sort toSort(ProductSortType sort) {
        return switch (sort) {
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price.amount");
            case LIKES_DESC, LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }
}
