package com.loopers.infrastructure.ranking;

import com.loopers.config.RankingProperties;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.ProductRanking;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

// ProductRanking 포트의 Redis Sorted Set 어댑터. 일별 key에 ZINCRBY 로 점수를 누적하고 TTL 을 재설정한다.
@Component
public class RedisProductRanking implements ProductRanking {

    static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private final RedisTemplate<String, String> redisTemplate;
    private final Duration ttl;

    public RedisProductRanking(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
            RankingProperties rankingProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = rankingProperties.ttl();
    }

    @Override
    public void addScore(Long productId, double delta) {
        String key = keyOf(LocalDate.now());
        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(productId), delta);
        redisTemplate.expire(key, ttl);  // 일별 key라서 매 적재 시 재설정해도 마지막 쓰기 후 TTL 만큼만 살아 자연 만료된다
    }

    static String keyOf(LocalDate date) {
        return KEY_PREFIX + date.format(YYYYMMDD);
    }
}