package com.loopers.domain.wishlist;

import java.util.List;
import java.util.Optional;

public interface WishlistRepository {
    WishlistModel save(WishlistModel wishlist);
    Optional<WishlistModel> findByUserIdAndProductId(Long userId, Long productId);
    List<WishlistModel> findAllByUserId(Long userId);
    List<WishlistProductSnapshot> findLikedProductSnapshotsByUserId(Long userId);
    int deleteByUserIdAndProductId(Long userId, Long productId);
}
