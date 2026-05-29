package com.loopers.infrastructure.wishlist;

import com.loopers.domain.wishlist.WishlistModel;
import com.loopers.domain.wishlist.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class WishlistRepositoryImpl implements WishlistRepository {

    private final WishlistJpaRepository wishlistJpaRepository;

    @Override
    public WishlistModel save(WishlistModel wishlist) {
        return wishlistJpaRepository.save(wishlist);
    }

    @Override
    public Optional<WishlistModel> findByUserIdAndProductId(Long userId, Long productId) {
        return wishlistJpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public List<WishlistModel> findAllByUserId(Long userId) {
        return wishlistJpaRepository.findAllByUserId(userId);
    }

    @Override
    public void delete(WishlistModel wishlist) {
        wishlistJpaRepository.delete(wishlist);
    }

    @Override
    public long countByProductId(Long productId) {
        return wishlistJpaRepository.countByProductId(productId);
    }

    @Override
    public Map<Long, Long> countsByProductIds(List<Long> productIds) {
        return wishlistJpaRepository.countGroupByProductIds(productIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }
}
