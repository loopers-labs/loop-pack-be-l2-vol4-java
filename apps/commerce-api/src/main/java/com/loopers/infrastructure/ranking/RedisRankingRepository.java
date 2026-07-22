package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.Rank;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 랭킹 조회 어댑터. 집계 스냅샷 읽기라 stale 을 감내하고 @Primary(REPLICA_PREFERRED) 템플릿을 쓴다(guide 결정 #6).
 * ZREVRANK 의 0-based 결과를 도메인 값 Rank(1-based)로 번역한다(+1 은 여기, 불변식은 Rank).
 */
@Component
public class RedisRankingRepository implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisRankingRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<RankingEntry> topN(RankingKey key, int page, int size) {
        long start = (long) page * size;
        long end = start + size - 1;
        Set<ZSetOperations.TypedTuple<String>> tuples =
            redisTemplate.opsForZSet().reverseRangeWithScores(key.value(), start, end);
        if (tuples == null) {
            return List.of();
        }
        return tuples.stream()
            .filter(tuple -> tuple.getValue() != null && tuple.getScore() != null)
            .map(tuple -> new RankingEntry(Long.parseLong(tuple.getValue()), tuple.getScore()))
            .toList();
    }

    @Override
    public Optional<Rank> rankOf(RankingKey key, long productId) {
        Long zeroBased = redisTemplate.opsForZSet().reverseRank(key.value(), String.valueOf(productId));
        return Optional.ofNullable(zeroBased).map(index -> Rank.of(index + 1));
    }

    @Override
    public long size(RankingKey key) {
        Long card = redisTemplate.opsForZSet().zCard(key.value());
        return Objects.requireNonNullElse(card, 0L);
    }
}
