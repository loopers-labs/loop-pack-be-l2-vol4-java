package com.loopers.tddstudy.infrastructure.ranking;

import com.loopers.tddstudy.domain.ranking.RankingItem;
import com.loopers.tddstudy.domain.ranking.RankingKey;
import com.loopers.tddstudy.domain.ranking.RankingRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Repository
public class RankingRedisRepository implements RankingRepository {

    private static final Duration TTL = Duration.ofDays(2);
    private final StringRedisTemplate redis;

    public RankingRedisRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void incrementScore(LocalDate date, Long productId, double delta) {
        String key = RankingKey.daily(date);
        redis.opsForZSet().incrementScore(key, String.valueOf(productId), delta);  // ZINCRBY
        redis.expire(key, TTL);
    }

    @Override
    public List<RankingItem> getPage(LocalDate date, int page, int size) {
        long start = (long) (page - 1) * size;
        long end = start + size - 1;
        Set<ZSetOperations.TypedTuple<String>> tuples = redis.opsForZSet()
                .reverseRangeWithScores(RankingKey.daily(date), start, end);       // ZREVRANGE WITHSCORES
        if (tuples == null) return List.of();
        return tuples.stream()
                .map(t -> new RankingItem(Long.valueOf(t.getValue()), t.getScore()))
                .toList();
    }

    @Override
    public Long getRank(LocalDate date, Long productId) {
        return redis.opsForZSet()
                .reverseRank(RankingKey.daily(date), String.valueOf(productId));   // ZREVRANK
    }
}
