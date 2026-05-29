package com.loopers.domain.wishlist;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface WishlistRepository {
    WishlistModel save(WishlistModel wishlist);
    Optional<WishlistModel> findByUserIdAndProductId(Long userId, Long productId);
    List<WishlistModel> findAllByUserId(Long userId);
    void delete(WishlistModel wishlist);
    long countByProductId(Long productId);
    Map<Long, Long> countsByProductIds(List<Long> productIds);
}
