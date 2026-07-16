package com.loopers.infrastructure.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.queue.WaitingQueue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class RedisWaitingQueue implements WaitingQueue {  // 대기열 포트의 Redis Sorted Set 어댑터. 정합성 위해 master 템플릿 사용

    static final String KEY = "queue:waiting";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisWaitingQueue(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void enter(String loginId) {
        // ZADD NX: 이미 있으면 score(최초 진입 시각)를 덮어쓰지 않아 순번이 뒤로 밀리지 않는다
        redisTemplate.opsForZSet().addIfAbsent(KEY, loginId, System.currentTimeMillis());
    }

    @Override
    public Optional<Long> rank(String loginId) {
        return Optional.ofNullable(redisTemplate.opsForZSet().rank(KEY, loginId));
    }

    @Override
    public long size() {
        Long count = redisTemplate.opsForZSet().zCard(KEY);
        return count == null ? 0L : count;
    }

    @Override
    public List<String> pollFirst(int n) {
        Set<ZSetOperations.TypedTuple<String>> popped = redisTemplate.opsForZSet().popMin(KEY, n);
        if (popped == null) {
            return List.of();
        }
        return popped.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .toList();
    }
}
