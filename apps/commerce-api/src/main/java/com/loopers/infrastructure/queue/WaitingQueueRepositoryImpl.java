package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.WaitingQueueRank;
import com.loopers.domain.queue.WaitingQueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class WaitingQueueRepositoryImpl implements WaitingQueueRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, String> masterRedisTemplate;

    public WaitingQueueRepositoryImpl(
            RedisTemplate<String, String> redisTemplate,
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.redisTemplate = redisTemplate;
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @Override
    public WaitingQueueRank enter(Long userId, long timestampMillis) {
        String member = String.valueOf(userId);
        masterRedisTemplate.opsForZSet().add(QueueRedisKeys.WAITING_QUEUE_KEY, member, timestampMillis);
        Long rank = masterRedisTemplate.opsForZSet().rank(QueueRedisKeys.WAITING_QUEUE_KEY, member);
        return rank == null ? null : new WaitingQueueRank(rank);
    }

    @Override
    public WaitingQueueRank rank(Long userId) {
        Long rank = redisTemplate.opsForZSet().rank(QueueRedisKeys.WAITING_QUEUE_KEY, String.valueOf(userId));
        return rank == null ? null : new WaitingQueueRank(rank);
    }

    @Override
    public Long size() {
        return redisTemplate.opsForZSet().zCard(QueueRedisKeys.WAITING_QUEUE_KEY);
    }
}
