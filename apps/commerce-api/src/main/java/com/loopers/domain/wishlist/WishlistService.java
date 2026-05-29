package com.loopers.domain.wishlist;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class WishlistService {

    private final WishlistRepository wishlistRepository;

    @Transactional
    public WishlistModel add(Long userId, Long productId) {
        if (wishlistRepository.findByUserIdAndProductId(userId, productId).isPresent()) {
            throw new CoreException(ErrorType.CONFLICT, "이미 찜한 상품입니다.");
        }
        return wishlistRepository.save(new WishlistModel(userId, productId));
    }

    @Transactional
    public void remove(Long userId, Long productId) {
        WishlistModel wishlist = wishlistRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "찜 목록에 존재하지 않는 상품입니다."));
        wishlistRepository.delete(wishlist);
    }

    @Transactional(readOnly = true)
    public List<WishlistModel> getList(Long userId) {
        return wishlistRepository.findAllByUserId(userId);
    }
}
