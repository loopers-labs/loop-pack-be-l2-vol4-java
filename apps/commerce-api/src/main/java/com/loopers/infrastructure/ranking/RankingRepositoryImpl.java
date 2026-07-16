package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRank;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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
        String key = RankingRedisKeys.dailyKey(date);
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

    @Override
    @CircuitBreaker(name = "redis")
    public long countRankings(LocalDate date) {
        String key = RankingRedisKeys.dailyKey(date);
        Long count = redisTemplate.opsForZSet().zCard(key);
        return count == null ? 0L : count;
    }

    @Override
    @CircuitBreaker(name = "redis")
    public ProductRank findRank(LocalDate date, Long productId) {
        String key = RankingRedisKeys.dailyKey(date);
        Long rank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
        return rank == null ? null : new ProductRank(rank);
    }
}
