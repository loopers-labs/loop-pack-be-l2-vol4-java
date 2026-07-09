package com.loopers.tddstudy.infrastructure.queue;

import com.loopers.tddstudy.domain.queue.WaitingQueueRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WaitingQueueRedisRepository implements WaitingQueueRepository {

    private static final String QUEUE_KEY = "waiting-queue";
    private final StringRedisTemplate redis;

    public WaitingQueueRedisRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean enqueue(Long userId, long timestamp) {
        Boolean added = redis.opsForZSet()
                .addIfAbsent(QUEUE_KEY, String.valueOf(userId), timestamp);  // ZADD NX
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Long getRank(Long userId) {
        return redis.opsForZSet().rank(QUEUE_KEY, String.valueOf(userId));   // ZRANK
    }

    @Override
    public long getTotalCount() {
        Long count = redis.opsForZSet().zCard(QUEUE_KEY);                    // ZCARD
        return count == null ? 0 : count;
    }

    @Override
    public java.util.List<Long> popMin(int count) {
        var popped = redis.opsForZSet().popMin(QUEUE_KEY, count);   // ZPOPMIN N
        if (popped == null) return java.util.List.of();
        return popped.stream().map(t -> Long.valueOf(t.getValue())).toList();
    }
}
