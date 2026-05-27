package com.loopers.domain.catalog.like;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProductLikeRepository {
    ProductLike save(ProductLike productLike);

    boolean saveIfAbsent(ProductLike productLike);

    Optional<ProductLike> find(String userId, Long productId);

    boolean exists(String userId, Long productId);

    void delete(ProductLike productLike);

    boolean delete(String userId, Long productId);

    List<ProductLike> findByUserId(String userId, int page, int size);

    Set<Long> findLikedProductIds(String userId, Collection<Long> productIds);

    long countByUserId(String userId);
}
