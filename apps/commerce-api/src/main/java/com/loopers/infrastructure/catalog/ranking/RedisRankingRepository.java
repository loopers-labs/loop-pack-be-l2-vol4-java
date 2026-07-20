package com.loopers.infrastructure.catalog.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.catalog.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RedisRankingRepository implements RankingRepository {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String KEY_PREFIX = "ranking:all:";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisRankingRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void incrementScore(LocalDate date, Long productId, double score, Duration ttl) {
        String key = key(date);
        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(productId), score);
        redisTemplate.expire(key, ttl);
    }

    @Override
    public List<Entry> findRankings(LocalDate date, int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = size <= 0 ? 20 : size;
        long start = (long) normalizedPage * normalizedSize;
        long end = start + normalizedSize - 1;

        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
            .reverseRangeWithScores(key(date), start, end);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        AtomicLong rank = new AtomicLong(start + 1);
        return tuples.stream()
            .filter(tuple -> tuple.getValue() != null && tuple.getScore() != null)
            .map(tuple -> new Entry(Long.valueOf(tuple.getValue()), rank.getAndIncrement(), tuple.getScore()))
            .toList();
    }

    @Override
    public Optional<Entry> findRank(LocalDate date, Long productId) {
        String key = key(date);
        String member = String.valueOf(productId);
        Long rank = redisTemplate.opsForZSet().reverseRank(key, member);
        if (rank == null) {
            return Optional.empty();
        }

        Double score = redisTemplate.opsForZSet().score(key, member);
        return Optional.of(new Entry(productId, rank + 1, score));
    }

    @Override
    public long count(LocalDate date) {
        Long count = redisTemplate.opsForZSet().zCard(key(date));
        return count == null ? 0L : count;
    }

    private String key(LocalDate date) {
        return KEY_PREFIX + DATE_FORMATTER.format(date);
    }
}
