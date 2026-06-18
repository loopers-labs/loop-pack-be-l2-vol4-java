package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeCountStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisLikeCountStore implements LikeCountStore {

    private static final Logger log = LoggerFactory.getLogger(RedisLikeCountStore.class);

    /** 좋아요 수 미반영 증감분 키. 배치(commerce-batch)도 동일 계약으로 SCAN/GETDEL 한다. */
    public static final String DELTA_KEY_PREFIX = "like:count:delta:";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void increment(Long productId) {
        applyDelta(productId, 1L);
    }

    @Override
    public void decrement(Long productId) {
        applyDelta(productId, -1L);
    }

    private void applyDelta(Long productId, long delta) {
        try {
            redisTemplate.opsForValue().increment(DELTA_KEY_PREFIX + productId, delta);
        } catch (RuntimeException e) {
            // Redis 장애 시에도 좋아요 관계(원천)는 이미 커밋됨. 증감분만 유실되며 product_like 재집계로 복구 가능.
            log.warn("좋아요 수 증감분 반영 실패 productId={}, delta={}", productId, delta, e);
        }
    }
}
