package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductLikeViewModel;
import com.loopers.domain.product.ProductLikeViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductLikeViewRepositoryImpl implements ProductLikeViewRepository {

    private final ProductLikeViewJpaRepository jpaRepository;

    @Override
    public ProductLikeViewModel save(ProductLikeViewModel view) {
        return jpaRepository.save(view);
    }

    @Override
    public Optional<ProductLikeViewModel> findByProductId(Long productId) {
        return jpaRepository.findById(productId);
    }

    @Override
    public Optional<ProductLikeViewModel> findByProductIdForUpdate(Long productId) {
        return jpaRepository.findByProductIdForUpdate(productId);
    }

    @Override
    public List<ProductLikeViewModel> findAllByProductIdIn(List<Long> productIds) {
        return jpaRepository.findAllByProductIdIn(productIds);
    }

    @Override
    public void deleteByProductId(Long productId) {
        jpaRepository.deleteByProductId(productId);
    }
}
