package com.loopers.application.like;

import com.loopers.config.CacheConfig;
import com.loopers.domain.like.ProductLikeService;
import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.like.ProductUnlikedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeFacade {
    private final ProductLikeService productLikeService;
    private final ApplicationEventPublisher eventPublisher;

    // 좋아요가 바뀌면 상세 응답의 likeCount 가 낡으므로, 해당 상품의 상세 캐시를 무효화한다.
    // 생성 이벤트는 애그리거트에 등록하지 않고(저장 전엔 ID 없음) 사실 확정 직후 응용 레이어인 여기서 발행한다.
    @CacheEvict(cacheNames = CacheConfig.PRODUCT_DETAIL, key = "#productId")
    @Transactional
    public void like(Long userId, Long productId) {
        if (productLikeService.like(userId, productId)) {
            eventPublisher.publishEvent(new ProductLikedEvent(userId, productId));
        }
    }

    @CacheEvict(cacheNames = CacheConfig.PRODUCT_DETAIL, key = "#productId")
    @Transactional
    public void unlike(Long userId, Long productId) {
        if (productLikeService.unlike(userId, productId)) {
            eventPublisher.publishEvent(new ProductUnlikedEvent(userId, productId));
        }
    }
}
