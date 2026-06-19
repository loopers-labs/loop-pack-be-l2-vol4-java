package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

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

    public void softDelete(Long productId) {
        ProductStatsModel stats = getByProductId(productId);
        stats.delete();
        productStatsRepository.save(stats);
    }

    public void increaseLikeCount(Long productId) {
        productStatsRepository.increaseLikeCount(productId);
    }

    public void decreaseLikeCount(Long productId) {
        productStatsRepository.decreaseLikeCount(productId);
    }

    public ProductStatsModel getByProductId(Long productId) {
        return productStatsRepository.findByProductId(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[productId = " + productId + "] 상품 통계를 찾을 수 없습니다."));
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
