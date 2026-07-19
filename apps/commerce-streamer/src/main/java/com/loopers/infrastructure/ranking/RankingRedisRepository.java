package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

@Component
public class RankingRedisRepository implements RankingRepository {

    // 시간 윈도우(1일)의 2배 — 자정 근처 컨슈머 지연, 콜드스타트 스케줄러 지연에 대한 안전 마진 (week9 qna Q2)
    private static final Duration TTL = Duration.ofDays(2);

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRedisRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void incrementScore(LocalDate day, Long productId, double score) {
        String key = RankingKeys.of(day);
        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(productId), score);

        Long remaining = redisTemplate.getExpire(key);
        if (remaining != null && remaining < 0) {
            redisTemplate.expire(key, TTL);
        }
    }
}
