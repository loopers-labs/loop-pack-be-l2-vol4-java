package com.loopers.infrastructure.like;

import com.loopers.domain.like.ProductLikeModel;
import com.loopers.domain.like.ProductLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductLikeRepositoryImpl implements ProductLikeRepository {

    private final ProductLikeJpaRepository productLikeJpaRepository;

    @Override
    public ProductLikeModel save(ProductLikeModel productLike) {
        return productLikeJpaRepository.save(productLike);
    }

    @Override
    public Optional<ProductLikeModel> find(String userLoginId, Long productId) {
        return productLikeJpaRepository.findByUserLoginIdAndProductId(userLoginId, productId);
    }

    @Override
    public void delete(ProductLikeModel productLike) {
        productLikeJpaRepository.delete(productLike);
    }
}
