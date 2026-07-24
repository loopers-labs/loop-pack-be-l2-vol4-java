package com.loopers.infrastructure.ranking;

import com.loopers.application.ranking.ActiveProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ActiveProductRepositoryImpl implements ActiveProductRepository {

    private final ProductBatchJpaRepository productRepository;

    @Override
    public Set<Long> findActiveProductIds(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Set.of();
        }
        return productRepository.findActiveProductIds(productIds);
    }
}
