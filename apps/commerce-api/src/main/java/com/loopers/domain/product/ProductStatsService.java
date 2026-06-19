package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductStatsService {

    private final ProductStatsRepository productStatsRepository;

    public ProductStatsModel create(ProductModel product) {
        return productStatsRepository.save(new ProductStatsModel(product));
    }

    public void delete(ProductModel product) {
        ProductStatsModel stats = getByProduct(product);
        stats.delete();
        productStatsRepository.save(stats);
    }

    // @Modifying UPDATE는 활성 트랜잭션이 필수 — LikeOutboxProcessor처럼 트랜잭션 없는 호출자도 안전하게 사용할 수 있도록 추가
    @Transactional
    public void increaseLikeCount(Long productId) {
        productStatsRepository.increaseLikeCount(productId);
    }

    // @Modifying UPDATE는 활성 트랜잭션이 필수 — LikeOutboxProcessor처럼 트랜잭션 없는 호출자도 안전하게 사용할 수 있도록 추가
    @Transactional
    public void decreaseLikeCount(Long productId) {
        productStatsRepository.decreaseLikeCount(productId);
    }

    public ProductStatsModel getByProduct(ProductModel product) {
        return productStatsRepository.findByProduct(product)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + product.getId() + "] 상품 통계를 찾을 수 없습니다."));
    }

    public Map<Long, ProductStatsModel> getMapByProductIds(Set<Long> productIds) {
        return productStatsRepository.findAllByProductIds(List.copyOf(productIds)).stream()
                .collect(Collectors.toMap(stats -> stats.getProduct().getId(), stats -> stats));
    }

    public Page<ProductStatsModel> findPage(Pageable pageable) {
        return productStatsRepository.findPageOrderByLikeCountDesc(pageable);
    }

    public Page<ProductStatsModel> findPageByProductIds(List<Long> productIds, Pageable pageable) {
        return productStatsRepository.findPageByProductIdsOrderByLikeCountDesc(productIds, pageable);
    }
}
