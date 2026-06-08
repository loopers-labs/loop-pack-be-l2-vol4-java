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
    public Optional<Product> findForUpdate(Long id) {
        return productJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public List<Product> findAll() {
        return productJpaRepository.findAll();
    }

    @Override
    public List<Product> findAll(Long brandId, ProductSortType sort, int page, int size) {
        if (sort == ProductSortType.LIKES_DESC) {
            // 좋아요 수는 집계라 derived query 로 표현 못 함 → JPQL 로 한 방 정렬.
            return productJpaRepository.findAllOrderByLikeCountDesc(brandId, PageRequest.of(page, size));
        }
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

    /**
     * LATEST 와 PRICE_ASC 는 단일 컬럼이라 Spring Data Sort 로 처리한다.
     * LIKES_DESC 는 위에서 별도 JPQL 로 처리하므로 여기 도달하지 않는다.
     */
    private Sort toSort(ProductSortType sort) {
        return switch (sort) {
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price");
            case LIKES_DESC, LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
    }
}
