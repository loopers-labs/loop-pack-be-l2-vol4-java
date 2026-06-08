package com.loopers.domain.wishlist;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class WishlistService {

    private final WishlistRepository wishlistRepository;

    @Transactional
    public WishlistModel add(Long userId, Long productId) {
        try {
            return wishlistRepository.save(new WishlistModel(userId, productId));
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 찜한 상품입니다.");
        }
    }

    @Transactional
    public void remove(Long userId, Long productId) {
        int deleted = wishlistRepository.deleteByUserIdAndProductId(userId, productId);
        if (deleted == 0) {
            throw new CoreException(ErrorType.NOT_FOUND, "찜 목록에 존재하지 않는 상품입니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<WishlistModel> getList(Long userId) {
        return wishlistRepository.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<WishlistProductSnapshot> getListWithDetails(Long userId) {
        return wishlistRepository.findLikedProductSnapshotsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long countByProductId(Long productId) {
        return wishlistRepository.countByProductId(productId);
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> countsByProductIds(List<Long> productIds) {
        return wishlistRepository.countsByProductIds(productIds);
    }
}
