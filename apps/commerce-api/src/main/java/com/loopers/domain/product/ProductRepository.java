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
    Page<ProductModel> search(Long brandId, SortOption sort, Pageable pageable);
    long countByBrandId(Long brandId);
    Map<Long, Long> countByBrandIds(Collection<Long> brandIds);
}
