package com.loopers.application.like;

import com.loopers.application.product.ProductCachePort;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 좋아요는 상품의 like_count 를 동기 갱신하므로, 상세 캐시는 stale 가능.
 * 좋아요/취소 직후 해당 상품의 상세 캐시를 무효화한다.
 * (목록 캐시는 TTL 1분으로 자연 만료 — like 변경 빈도에 비해 evict 비용이 더 크다고 판단)
 */
@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final ProductCachePort productCache;

    public void like(Long userId, Long productId) {
        likeService.like(userId, productId);
        productCache.evictDetail(productId);
    }

    public void unlike(Long userId, Long productId) {
        likeService.unlike(userId, productId);
        productCache.evictDetail(productId);
    }

    public List<LikeInfo> getMyLikes(Long userId) {
        List<LikeModel> likes = likeService.findAllByUser(userId);
        return likes.stream().map(LikeInfo::from).toList();
    }
}
