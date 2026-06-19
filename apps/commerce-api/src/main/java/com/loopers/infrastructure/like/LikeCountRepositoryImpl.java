package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeCountRepository;
import com.loopers.domain.like.ProductLikeCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeCountRepositoryImpl implements LikeCountRepository {

    private final ProductLikeCountJpaRepository jpaRepository;

    @Override
    public void increase(Long productId) {
        jpaRepository.increase(productId);
    }

    @Override
    public void decrease(Long productId) {
        jpaRepository.decrease(productId);
    }

    @Override
    public Optional<ProductLikeCount> find(Long productId) {
        return jpaRepository.findByProductId(productId);
    }

    @Override
    public List<ProductLikeCount> findAllByProductIds(List<Long> productIds) {
        return jpaRepository.findByProductIdIn(productIds);
    }

    @Override
    public ProductLikeCount save(ProductLikeCount likeCount) {
        return jpaRepository.save(likeCount);
    }
}
