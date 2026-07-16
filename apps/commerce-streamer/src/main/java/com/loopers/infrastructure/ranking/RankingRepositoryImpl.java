package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
public class RankingRepositoryImpl implements RankingRepository {

    private final RedisTemplate<String, String> masterRedisTemplate;

    public RankingRepositoryImpl(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
    }

    // 배치로 집계된 productId별 점수 델타를 ZINCRBY로 반영한다.
    // 매 반영마다 TTL을 다시 설정해, 당일 활동이 이어지는 동안은 키가 유지되고
    // 마지막 반영 시점으로부터 2일 뒤에는 자연스럽게 만료되도록 한다.
    @Override
    public void incrementScores(LocalDate date, Map<Long, Double> productScoreDeltas) {
        if (productScoreDeltas.isEmpty()) return;

        String key = RankingRedisKeys.dailyKey(date);
        productScoreDeltas.forEach((productId, delta) ->
                masterRedisTemplate.opsForZSet().incrementScore(key, String.valueOf(productId), delta)
        );
        masterRedisTemplate.expire(key, RankingRedisKeys.TTL);
    }
}
