package com.loopers.infrastructure.like;

import com.loopers.domain.like.ProductLike;
import com.loopers.domain.like.ProductLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductLikeRepositoryImpl implements ProductLikeRepository {

    private final ProductLikeJpaRepository productLikeJpaRepository;

    @Override
    public ProductLike save(ProductLike productLike) {
        return productLikeJpaRepository.save(ProductLikeJpaEntity.from(productLike))
            .toDomain();
    }

    @Override
    public Optional<ProductLike> find(String userLoginId, Long productId) {
        return productLikeJpaRepository.findByUserLoginIdAndProductId(userLoginId, productId)
            .map(ProductLikeJpaEntity::toDomain);
    }

    @Override
    public List<ProductLike> findAllByUserLoginId(String userLoginId) {
        return productLikeJpaRepository.findAllByUserLoginId(userLoginId).stream()
            .map(ProductLikeJpaEntity::toDomain)
            .toList();
    }

    @Override
    public void delete(ProductLike productLike) {
        productLikeJpaRepository.findByUserLoginIdAndProductId(
            productLike.getUserLoginId(),
            productLike.getProductId()
        ).ifPresent(productLikeJpaRepository::delete);
    }
}
