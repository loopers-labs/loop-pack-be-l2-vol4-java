package com.loopers.application.like;

import com.loopers.application.product.ProductCachePolicy;
import com.loopers.infrastructure.cache.RedisCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductLikeFacade {

    private final ProductLikeService productLikeService;
    private final RedisCacheRepository cacheRepository;

    public void like(String userId, Long productId) {
        boolean changed = productLikeService.like(userId, productId);
        // 트랜잭션(Service) 커밋 이후에만 무효화해 롤백-무효화 순서 역전을 방지한다.
        // 목록 캐시는 짧은 TTL 로 수렴시키므로 상세만 무효화한다.
        if (changed) {
            cacheRepository.evict(ProductCachePolicy.detailKey(productId));
        }
    }

    public void unlike(String userId, Long productId) {
        boolean changed = productLikeService.unlike(userId, productId);
        if (changed) {
            cacheRepository.evict(ProductCachePolicy.detailKey(productId));
        }
    }
}
