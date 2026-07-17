package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class RankingRepositoryImpl implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRepositoryImpl(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<RankingEntry> getTopN(String key, long offset, long limit) {
        long start = offset;
        long end = offset + limit - 1;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (tuples == null) {
            return List.of();
        }
        return tuples.stream()
            .filter(t -> t.getValue() != null && t.getScore() != null)
            .map(t -> new RankingEntry(Long.valueOf(t.getValue()), t.getScore()))
            .toList();
    }

    @Override
    public Optional<Long> getRank(String key, Long productId) {
        return Optional.ofNullable(redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId)));
    }

    @Override
    public long getTotalCount(String key) {
        Long count = redisTemplate.opsForZSet().zCard(key);
        return count == null ? 0L : count;
    }
}
