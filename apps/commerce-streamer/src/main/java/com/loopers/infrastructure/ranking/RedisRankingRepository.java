package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 랭킹 점수를 ZINCRBY 로 누적하는 어댑터. 정확한 누적이 필요하므로 master 템플릿을 쓴다(guide 결정 #6).
 * member 규약: productId 를 그대로 문자열화(예: "101").
 */
@Component
public class RedisRankingRepository implements RankingRepository {

    private final RedisTemplate<String, String> masterRedisTemplate;

    public RedisRankingRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
    }

    @Override
    public void incrementScore(RankingKey key, long productId, double delta) {
        String rankingKey = key.value();
        masterRedisTemplate.opsForZSet().incrementScore(rankingKey, String.valueOf(productId), delta);
        // 최초 쓰기(TTL 미설정)에만 만료를 건다 — 이후 쓰기가 만료 창을 미끄러뜨리지 않도록.
        Long ttlSeconds = masterRedisTemplate.getExpire(rankingKey, TimeUnit.SECONDS);
        if (ttlSeconds != null && ttlSeconds < 0) {   // -1: TTL 없음, -2: 키 없음
            masterRedisTemplate.expire(rankingKey, key.ttl());
        }
    }
}
