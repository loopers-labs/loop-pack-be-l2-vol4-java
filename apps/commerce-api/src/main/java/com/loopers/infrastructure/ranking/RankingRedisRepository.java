package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingKey;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Redis Sorted Set 기반 랭킹 조회 구현체.
 * 조회 전용이고 약간의 복제 지연이 허용되므로(랭킹은 근사 지표) 기본(replica-preferred) 템플릿을 사용한다.
 */
@Repository
public class RankingRedisRepository implements RankingRepository {

    /**
     * "양수 점수" 필터의 하한. 논리적 최소 양수 점수는 조회 1건(0.1)이고,
     * 가감산이 상쇄된 잔여 부동소수점 오차(1e-16 수준)는 이 값보다 작아 0으로 취급된다.
     */
    private static final double MIN_POSITIVE_SCORE = 1e-7;

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<Long> findTopProductIds(LocalDate date, long offset, int limit) {
        Set<String> members = redisTemplate.opsForZSet()
            .reverseRangeByScore(RankingKey.daily(date), MIN_POSITIVE_SCORE, Double.POSITIVE_INFINITY, offset, limit);
        if (members == null) {
            return List.of();
        }
        return members.stream().map(Long::valueOf).toList();
    }

    @Override
    public long countRanked(LocalDate date) {
        Long count = redisTemplate.opsForZSet()
            .count(RankingKey.daily(date), MIN_POSITIVE_SCORE, Double.POSITIVE_INFINITY);
        return count == null ? 0L : count;
    }

    @Override
    public Optional<Long> findRank(LocalDate date, Long productId) {
        String key = RankingKey.daily(date);
        String member = String.valueOf(productId);

        Double score = redisTemplate.opsForZSet().score(key, member);
        if (score == null || score < MIN_POSITIVE_SCORE) {
            return Optional.empty();
        }
        // ZREVRANK는 0-based — 자신보다 점수가 높은 멤버 수만 세므로, 0 이하 점수 멤버가 순위에 영향을 주지 않는다
        Long rank = redisTemplate.opsForZSet().reverseRank(key, member);
        return Optional.ofNullable(rank).map(r -> r + 1);
    }
}
