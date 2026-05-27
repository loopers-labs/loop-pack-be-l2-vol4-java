package com.loopers.infrastructure.catalog.like;

import com.loopers.domain.catalog.like.ProductLike;
import com.loopers.domain.catalog.like.ProductLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class ProductLikeRepositoryImpl implements ProductLikeRepository {

    private final ProductLikeJpaRepository productLikeJpaRepository;

    @Override
    public ProductLike save(ProductLike productLike) {
        return productLikeJpaRepository.save(ProductLikeJpaEntity.from(productLike)).toDomain();
    }

    @Override
    public boolean saveIfAbsent(ProductLike productLike) {
        return productLikeJpaRepository.insertIgnore(productLike.getUserId(), productLike.getProductId()) > 0;
    }

    @Override
    public Optional<ProductLike> find(String userId, Long productId) {
        return productLikeJpaRepository.findByUserIdAndProductId(userId, productId).map(ProductLikeJpaEntity::toDomain);
    }

    @Override
    public boolean exists(String userId, Long productId) {
        return productLikeJpaRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public void delete(ProductLike productLike) {
        productLikeJpaRepository.deleteByUserIdAndProductId(productLike.getUserId(), productLike.getProductId());
    }

    @Override
    public boolean delete(String userId, Long productId) {
        return productLikeJpaRepository.deleteByUserIdAndProductId(userId, productId) > 0;
    }

    @Override
    public List<ProductLike> findByUserId(String userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return productLikeJpaRepository.findByUserId(userId, pageRequest)
            .stream()
            .map(ProductLikeJpaEntity::toDomain)
            .toList();
    }

    @Override
    public Set<Long> findLikedProductIds(String userId, Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Set.of();
        }

        return productLikeJpaRepository.findLikedProductIds(userId, productIds);
    }

    @Override
    public long countByUserId(String userId) {
        return productLikeJpaRepository.countByUserId(userId);
    }
}
