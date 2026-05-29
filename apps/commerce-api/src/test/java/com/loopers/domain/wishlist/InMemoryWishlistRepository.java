package com.loopers.domain.wishlist;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

class InMemoryWishlistRepository implements WishlistRepository {

    private final List<WishlistModel> store = new ArrayList<>();

    @Override
    public WishlistModel save(WishlistModel wishlist) {
        store.add(wishlist);
        return wishlist;
    }

    @Override
    public Optional<WishlistModel> findByUserIdAndProductId(Long userId, Long productId) {
        return store.stream()
                .filter(w -> w.getUserId().equals(userId) && w.getProductId().equals(productId))
                .findFirst();
    }

    @Override
    public List<WishlistModel> findAllByUserId(Long userId) {
        return store.stream()
                .filter(w -> w.getUserId().equals(userId))
                .toList();
    }

    @Override
    public void delete(WishlistModel wishlist) {
        store.removeIf(w -> w.getUserId().equals(wishlist.getUserId()) && w.getProductId().equals(wishlist.getProductId()));
    }

    @Override
    public long countByProductId(Long productId) {
        return store.stream().filter(w -> w.getProductId().equals(productId)).count();
    }

    @Override
    public Map<Long, Long> countsByProductIds(List<Long> productIds) {
        return store.stream()
                .filter(w -> productIds.contains(w.getProductId()))
                .collect(Collectors.groupingBy(WishlistModel::getProductId, Collectors.counting()));
    }
}
