    package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.OrderQueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class OrderQueueRedisRepository implements OrderQueueRepository {

    private static final String QUEUE_KEY = "waiting-queue";

    private final RedisTemplate<String, String> redisTemplate;

    public OrderQueueRedisRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void enter(Long userId, long score) {
        // addIfAbsent = ZADD NX. 이미 있으면 score 를 덮어쓰지 않아 기존 순번을 유지한다.
        redisTemplate.opsForZSet().addIfAbsent(QUEUE_KEY, String.valueOf(userId), score);
    }

    @Override
    public Optional<Long> findRank(Long userId) {
        Long rank = redisTemplate.opsForZSet().rank(QUEUE_KEY, String.valueOf(userId));
        return Optional.ofNullable(rank);
    }

    @Override
    public long size() {
        Long size = redisTemplate.opsForZSet().zCard(QUEUE_KEY);
        return size == null ? 0L : size;
    }
}
