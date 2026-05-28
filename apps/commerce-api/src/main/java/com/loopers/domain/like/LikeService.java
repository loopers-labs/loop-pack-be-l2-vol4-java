package com.loopers.domain.like;

import com.loopers.domain.product.ProductModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;

    public boolean like(Long userId, Long productId) {
        return likeRepository.saveIfAbsent(new LikeModel(userId, productId));
    }

    public boolean unlike(Long userId, Long productId) {
        return likeRepository.deleteByUserIdAndProductId(userId, productId) > 0;
    }

    @Transactional(readOnly = true)
    public List<ProductModel> getMyLikedActiveProducts(Long userId, int page, int size) {
        return likeRepository.findLikedActiveProductsByUserId(userId, page, size);
    }
}
