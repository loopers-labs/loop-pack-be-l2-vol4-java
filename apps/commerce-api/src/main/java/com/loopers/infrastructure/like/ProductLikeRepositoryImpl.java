package com.loopers.infrastructure.like;

import com.loopers.domain.like.ProductLike;
import com.loopers.domain.like.ProductLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductLikeRepositoryImpl implements ProductLikeRepository {

    private final ProductLikeJpaRepository productLikeJpaRepository;

    @Override
    public ProductLike save(ProductLike domain) {
        return productLikeJpaRepository.save(ProductLikeEntity.from(domain)).toDomain();
    }

    @Override
    public boolean existsByUserIdAndProductId(Long userId, Long productId) {
        return productLikeJpaRepository.existsByIdUserIdAndIdProductId(userId, productId);
    }

    @Override
    public void deleteByUserIdAndProductId(Long userId, Long productId) {
        productLikeJpaRepository.deleteByIdUserIdAndIdProductId(userId, productId);
    }

    @Override
    public List<ProductLike> findAllByUserId(Long userId) {
        return productLikeJpaRepository.findAllByIdUserId(userId).stream()
            .map(ProductLikeEntity::toDomain)
            .toList();
    }
}
