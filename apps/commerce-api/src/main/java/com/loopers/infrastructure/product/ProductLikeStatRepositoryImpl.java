package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductLikeStat;
import com.loopers.domain.product.ProductLikeStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductLikeStatRepositoryImpl implements ProductLikeStatRepository {

    private final ProductLikeStatJpaRepository productLikeStatJpaRepository;

    @Override
    public ProductLikeStat save(ProductLikeStat stat) {
        return productLikeStatJpaRepository.save(stat);
    }

    @Override
    public Optional<ProductLikeStat> find(Long productId) {
        return productLikeStatJpaRepository.findById(productId);
    }

    @Override
    public List<ProductLikeStat> findAllByProductIdIn(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        return productLikeStatJpaRepository.findAllByProductIdIn(productIds);
    }

    @Override
    public void saveAll(List<ProductLikeStat> stats) {
        productLikeStatJpaRepository.saveAll(stats);
    }

    @Override
    public long count() {
        return productLikeStatJpaRepository.count();
    }
}
