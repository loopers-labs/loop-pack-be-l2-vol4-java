package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);
    Optional<ProductModel> findById(Long id);
    List<ProductModel> findAllByIds(Collection<Long> ids);
    /** soft-delete 필터링된 존재 여부 — 가벼운 검증용 (엔티티 적재 없이 SELECT 1). */
    boolean existsById(Long id);
    Page<ProductModel> search(Long brandId, SortOption sort, Pageable pageable);
    long countByBrandId(Long brandId);
    Map<Long, Long> countByBrandIds(Collection<Long> brandIds);
}
