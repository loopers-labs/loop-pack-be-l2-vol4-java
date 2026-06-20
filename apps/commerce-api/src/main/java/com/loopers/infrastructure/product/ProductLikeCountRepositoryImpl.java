package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductLikeCountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@RequiredArgsConstructor
@Repository
public class ProductLikeCountRepositoryImpl implements ProductLikeCountRepository {

    private static final String PENDING_KEY_PREFIX = "product:like:pending:";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void increment(Long productId) {
        try {
            redisTemplate.opsForValue().increment(PENDING_KEY_PREFIX + productId);
        } catch (Exception e) {
            log.warn("Redis likeCount increment 실패, productId={}", productId, e);
        }
    }

    @Override
    public void decrement(Long productId) {
        try {
            redisTemplate.opsForValue().decrement(PENDING_KEY_PREFIX + productId);
        } catch (Exception e) {
            log.warn("Redis likeCount decrement 실패, productId={}", productId, e);
        }
    }
}