package com.loopers.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    ProductModel save(ProductModel product);

    Optional<ProductModel> find(Long id);

    List<ProductModel> findAllByIds(List<Long> ids);

    List<ProductModel> findAllByBrandId(Long brandId);

    Page<ProductModel> findAllByBrandId(Long brandId, Pageable pageable);

    Page<ProductModel> findProducts(Long brandId, Pageable pageable);

    // 동시성 안전: 원자적 UPDATE로 처리하여 race condition 없이 카운트 정합성을 보장한다.
    void increaseLikeCount(Long id);

    // 동시성 안전: 원자적 UPDATE로 처리하여 race condition 없이 카운트 정합성을 보장한다.
    void decreaseLikeCount(Long id);

    void softDeleteAllByBrandId(Long brandId, ZonedDateTime deletedAt);
}
