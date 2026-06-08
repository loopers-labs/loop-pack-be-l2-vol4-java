package com.loopers.application.wishlist;

import com.loopers.domain.wishlist.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class WishlistFacade {

    private final WishlistService wishlistService;

    public void addLike(Long userId, Long productId) {
        wishlistService.add(userId, productId);
    }

    public void removeLike(Long userId, Long productId) {
        wishlistService.remove(userId, productId);
    }

    public List<WishlistInfo> getLikedProducts(Long userId) {
        return wishlistService.getListWithDetails(userId)
                .stream()
                .map(WishlistInfo::from)
                .toList();
    }
}
