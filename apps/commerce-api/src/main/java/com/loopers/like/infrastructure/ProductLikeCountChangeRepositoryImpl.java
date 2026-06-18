package com.loopers.like.infrastructure;

import com.loopers.like.domain.ProductLikeCountChange;
import com.loopers.like.domain.ProductLikeCountChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductLikeCountChangeRepositoryImpl implements ProductLikeCountChangeRepository {

    private final ProductLikeCountChangeJpaRepository productLikeCountChangeJpaRepository;

    @Override
    public ProductLikeCountChange save(ProductLikeCountChange change) {
        return productLikeCountChangeJpaRepository.save(change);
    }
}
