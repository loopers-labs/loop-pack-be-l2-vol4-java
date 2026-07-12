package com.loopers.infrastructure.ordering.queue;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ordering.queue.OrderQueueRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class RedisOrderQueueRepository implements OrderQueueRepository {
    private static final String WAITING_KEY = "commerce:ordering:queue:v1:waiting";
    private static final String SEQUENCE_KEY = "commerce:ordering:queue:v1:sequence";
    private static final String TOKEN_PREFIX = "commerce:ordering:queue:v1:token:";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisOrderQueueRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Entry enter(String userId) {
        Long sequence = redisTemplate.opsForValue().increment(SEQUENCE_KEY);
        if (sequence == null) {
            throw new IllegalStateException("Failed to issue order queue sequence.");
        }

        Boolean added = redisTemplate.opsForZSet().addIfAbsent(WAITING_KEY, userId, sequence.doubleValue());
        Double score = Boolean.TRUE.equals(added)
            ? sequence.doubleValue()
            : redisTemplate.opsForZSet().score(WAITING_KEY, userId);

        if (score == null) {
            throw new IllegalStateException("Failed to read order queue score.");
        }

        return new Entry(userId, score.longValue(), rank(userId).orElseThrow());
    }

    @Override
    public Optional<Long> rank(String userId) {
        Long rank = redisTemplate.opsForZSet().rank(WAITING_KEY, userId);
        return rank == null ? Optional.empty() : Optional.of(rank + 1);
    }

    @Override
    public long waitingCount() {
        Long size = redisTemplate.opsForZSet().zCard(WAITING_KEY);
        return size == null ? 0L : size;
    }

    @Override
    public String issueToken(String userId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(tokenKey(userId), token, ttl);
        return token;
    }

    @Override
    public Optional<String> findToken(String userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(tokenKey(userId)));
    }

    @Override
    public boolean isValidToken(String userId, String token) {
        return token != null && findToken(userId)
            .map(token::equals)
            .orElse(false);
    }

    @Override
    public void deleteToken(String userId) {
        redisTemplate.delete(tokenKey(userId));
    }

    @Override
    public List<Admitted> admitNext(int count, Duration ttl) {
        if (count <= 0) {
            return List.of();
        }

        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(WAITING_KEY, count);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        return tuples.stream()
            .filter(tuple -> tuple.getValue() != null)
            .sorted(Comparator.comparing(tuple -> Optional.ofNullable(tuple.getScore()).orElse(Double.MAX_VALUE)))
            .map(tuple -> {
                String userId = tuple.getValue();
                return new Admitted(userId, issueToken(userId, ttl));
            })
            .toList();
    }

    private String tokenKey(String userId) {
        return TOKEN_PREFIX + userId;
    }
}
