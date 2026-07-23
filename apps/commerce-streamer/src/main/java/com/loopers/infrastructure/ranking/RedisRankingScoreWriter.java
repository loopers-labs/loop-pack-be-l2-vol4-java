package com.loopers.infrastructure.ranking;

import com.loopers.application.ranking.RankingScoreWriter;
import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.DailyRankingKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisRankingScoreWriter implements RankingScoreWriter {
    private static final long TTL_SECONDS = 2L * 24 * 60 * 60;
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
        redis.call('ZINCRBY', KEYS[1], ARGV[2], ARGV[1])
        redis.call('EXPIRE', KEYS[1], ARGV[3])
        return 1
        """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisRankingScoreWriter(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void increment(String rankingKey, Long productId, double scoreDelta) {
        redisTemplate.execute(
            INCREMENT_SCRIPT,
            List.of(rankingKey),
            DailyRankingKey.member(productId),
            Double.toString(scoreDelta),
            Long.toString(TTL_SECONDS)
        );
    }
}
