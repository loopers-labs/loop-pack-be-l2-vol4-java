package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Redis Sorted Set 기반 랭킹 저장소 — ZINCRBY로 점수를 누적한다.
 * 쓰기 직후 읽기 일관성이 필요한 대기열과 달리 랭킹은 쓰기 전용이지만,
 * replica-preferred 기본 템플릿은 getExpire(읽기)가 복제 지연으로 새 키를 못 볼 수 있어 master 템플릿을 사용한다.
 */
@Repository
public class RankingRedisRepository implements RankingRepository {

    private static final long TTL_NOT_SET = -1L;

    /** "양수 점수" 필터의 하한 — 논리적 최소 양수 점수(조회 0.1)와 부동소수점 잔여 오차 사이 값 (commerce-api 조회 측과 동일 기준). */
    private static final double MIN_POSITIVE_SCORE = 1e-7;

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRedisRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void incrementScore(LocalDate date, Long productId, double scoreDelta) {
        String key = RankingKey.daily(date);
        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(productId), scoreDelta);
        setTtlOnCreation(key);
    }

    @Override
    public List<RankingEntry> findTopEntries(LocalDate date, int limit) {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
            .reverseRangeByScoreWithScores(RankingKey.daily(date), MIN_POSITIVE_SCORE, Double.POSITIVE_INFINITY, 0, limit);
        if (tuples == null) {
            return List.of();
        }
        return tuples.stream()
            .map(tuple -> new RankingEntry(Long.valueOf(tuple.getValue()), tuple.getScore()))
            .toList();
    }

    @Override
    public void saveScoreIfAbsent(LocalDate date, Long productId, double score) {
        String key = RankingKey.daily(date);
        redisTemplate.opsForZSet().addIfAbsent(key, String.valueOf(productId), score);
        setTtlOnCreation(key);
    }

    /**
     * TTL은 키 생성 시 1회만 설정한다 — 매 가산마다 갱신하면 쓰기가 이어지는 동안 만료가 계속 밀린다.
     */
    private void setTtlOnCreation(String key) {
        Long expire = redisTemplate.getExpire(key);
        if (expire != null && expire == TTL_NOT_SET) {
            redisTemplate.expire(key, RankingKey.DAILY_TTL);
        }
    }
}
