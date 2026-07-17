package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RankingRepositoryImpl implements RankingRepository {

    private static final Duration TTL = Duration.ofDays(2);

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRepositoryImpl(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void incrementScore(String key, Long productId, double score) {
        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(productId), score);
        redisTemplate.expire(key, TTL);
    }
}
