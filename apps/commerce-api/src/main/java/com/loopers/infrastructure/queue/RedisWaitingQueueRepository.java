package com.loopers.infrastructure.queue;

import com.loopers.application.queue.WaitingQueueRepository;
import com.loopers.config.redis.RedisConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class RedisWaitingQueueRepository implements WaitingQueueRepository {

    private static final String WAITING_QUEUE_KEY = "order:waiting-queue";
    private static final String ENTRY_TOKEN_KEY_FORMAT = "order:entry-token:%s";

    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean enqueueIfAbsent(String userLoginId, double score) {
        Boolean added = redisTemplate.opsForZSet().addIfAbsent(WAITING_QUEUE_KEY, userLoginId, score);
        return Boolean.TRUE.equals(added);
    }

    @Override
    public Optional<Long> findRank(String userLoginId) {
        return Optional.ofNullable(redisTemplate.opsForZSet().rank(WAITING_QUEUE_KEY, userLoginId));
    }

    @Override
    public long countWaitingUsers() {
        Long count = redisTemplate.opsForZSet().zCard(WAITING_QUEUE_KEY);
        return count == null ? 0L : count;
    }

    @Override
    public List<String> popWaitingUsers(int count) {
        if (count <= 0) {
            return List.of();
        }
        return redisTemplate.opsForZSet().popMin(WAITING_QUEUE_KEY, count).stream()
            .map(ZSetOperations.TypedTuple::getValue)
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public void issueEntryToken(String userLoginId, String token, Duration ttl) {
        redisTemplate.opsForValue().set(entryTokenKey(userLoginId), token, ttl);
    }

    @Override
    public Optional<String> findEntryToken(String userLoginId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(entryTokenKey(userLoginId)));
    }

    @Override
    public void deleteEntryToken(String userLoginId) {
        redisTemplate.delete(entryTokenKey(userLoginId));
    }

    private String entryTokenKey(String userLoginId) {
        return ENTRY_TOKEN_KEY_FORMAT.formatted(userLoginId);
    }
}
