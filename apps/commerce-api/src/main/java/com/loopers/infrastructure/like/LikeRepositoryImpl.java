package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductModel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public boolean saveIfAbsent(LikeModel like) {
        if (likeJpaRepository.existsByUserIdAndProductId(like.getUserId(), like.getProductId())) {
            return false;
        }
        likeJpaRepository.save(like);
        return true;
    }

    @Override
    public int deleteByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.deleteByUserIdAndProductId(userId, productId);
    }

    @Override
    public List<ProductModel> findLikedActiveProductsByUserId(Long userId, int page, int size) {
        return likeJpaRepository.findLikedActiveProductsByUserId(userId, PageRequest.of(page, size));
    }
}
