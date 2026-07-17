package com.loopers.infrastructure.ranking;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

@Repository
public class RankingRedisRepository {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter KEY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Duration TTL = Duration.ofDays(2);

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRedisRepository(
        @Qualifier("masterRedisTemplate") RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    public void addScore(Long productId, double score, ZonedDateTime occurredAt) {
        String key = buildKey(occurredAt);
        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(productId), score);
        redisTemplate.expire(key, TTL);
    }

    /**
     * fromDate 랭킹의 상위 topN개를 decayFactor만큼 감쇠시켜 toDate 랭킹에 미리 심어둔다.
     * (예: decayFactor=0.1 -> 오늘 점수의 10%만 이월)
     * 콜드스타트 완화를 위한 것이라 누적(increment)이 아니라 덮어쓰기(ZADD)로 반영한다.
     */
    public void carryOverTopScores(LocalDate fromDate, LocalDate toDate, int topN, double decayFactor) {
        String fromKey = buildKey(fromDate);
        String toKey = buildKey(toDate);

        Set<ZSetOperations.TypedTuple<String>> topEntries = redisTemplate.opsForZSet()
            .reverseRangeWithScores(fromKey, 0, topN - 1);
        if (topEntries == null || topEntries.isEmpty()) {
            return;
        }

        for (ZSetOperations.TypedTuple<String> entry : topEntries) {
            String productId = entry.getValue();
            Double score = entry.getScore();
            if (productId == null || score == null) {
                continue;
            }
            redisTemplate.opsForZSet().add(toKey, productId, score * decayFactor);
        }
        redisTemplate.expire(toKey, TTL);
    }

    private String buildKey(ZonedDateTime occurredAt) {
        return buildKey(occurredAt.withZoneSameInstant(ZONE).toLocalDate());
    }

    private String buildKey(LocalDate date) {
        return KEY_PREFIX + date.format(KEY_DATE_FORMAT);
    }
}
