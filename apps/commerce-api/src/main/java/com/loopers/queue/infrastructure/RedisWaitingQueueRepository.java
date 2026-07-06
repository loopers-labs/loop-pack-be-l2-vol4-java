package com.loopers.queue.infrastructure;

import com.loopers.queue.domain.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisWaitingQueueRepository implements WaitingQueueRepository {

    private static final String WAITING_KEY = "order-queue:waiting";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean add(String userId, long score) {
        Boolean added = redisTemplate.opsForZSet().addIfAbsent(WAITING_KEY, userId, score);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Long rank(String userId) {
        return redisTemplate.opsForZSet().rank(WAITING_KEY, userId);
    }

    @Override
    public long size() {
        Long count = redisTemplate.opsForZSet().zCard(WAITING_KEY);
        return count == null ? 0L : count;
    }
}
