package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRank;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class RankingRepositoryImpl implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRepositoryImpl(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    @CircuitBreaker(name = "redis")
    public List<RankingEntry> findRankings(LocalDate date, long start, long end) {
        return findRankings(RankingRedisKeys.dailyKey(date), start, end);
    }

    @Override
    @CircuitBreaker(name = "redis")
    public long countRankings(LocalDate date) {
        return countRankings(RankingRedisKeys.dailyKey(date));
    }

    @Override
    @CircuitBreaker(name = "redis")
    public ProductRank findRank(LocalDate date, Long productId) {
        String key = RankingRedisKeys.dailyKey(date);
        Long rank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
        return rank == null ? null : new ProductRank(rank);
    }

    @Override
    @CircuitBreaker(name = "redis")
    public List<RankingEntry> findHourlyRankings(LocalDateTime dateTime, long start, long end) {
        return findRankings(RankingRedisKeys.hourlyKey(dateTime), start, end);
    }

    @Override
    @CircuitBreaker(name = "redis")
    public long countHourlyRankings(LocalDateTime dateTime) {
        return countRankings(RankingRedisKeys.hourlyKey(dateTime));
    }

    private List<RankingEntry> findRankings(String key, long start, long end) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<RankingEntry> entries = new ArrayList<>();
        long rank = start + 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            entries.add(new RankingEntry(Long.valueOf(tuple.getValue()), rank++, tuple.getScore()));
        }
        return entries;
    }

    private long countRankings(String key) {
        Long count = redisTemplate.opsForZSet().zCard(key);
        return count == null ? 0L : count;
    }
}
