package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankedProductEntry;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingQueryRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 랭킹 조회 어댑터 — 표시용 읽기 전용이라 기본 템플릿(REPLICA_PREFERRED)을 쓴다.
 * (대기열과 달리 쓰기 직후 강한 일관성이 필요 없어 replica 지연을 허용한다.)
 */
@Component
public class RankingQueryRepositoryImpl implements RankingQueryRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RankingQueryRepositoryImpl(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<RankedProductEntry> findPage(LocalDate date, int offset, int size) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
            .reverseRangeWithScores(RankingKeys.daily(date), offset, offset + size - 1L);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        return tuples.stream()
            .map(t -> new RankedProductEntry(
                Long.valueOf(t.getValue()),
                t.getScore() == null ? 0.0 : t.getScore()))
            .toList(); // LinkedHashSet — 점수 내림차순 유지
    }

    @Override
    public Optional<Long> findRank(LocalDate date, Long productId) {
        return Optional.ofNullable(
            redisTemplate.opsForZSet().reverseRank(RankingKeys.daily(date), String.valueOf(productId)));
    }

    @Override
    public long countRanked(LocalDate date) {
        Long count = redisTemplate.opsForZSet().zCard(RankingKeys.daily(date));
        return count != null ? count : 0L;
    }
}
