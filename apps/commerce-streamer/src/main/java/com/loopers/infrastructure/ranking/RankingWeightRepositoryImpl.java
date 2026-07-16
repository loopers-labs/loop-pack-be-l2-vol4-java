package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingWeightRepository;
import com.loopers.domain.ranking.RankingWeights;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

// 가중치를 재배포 없이 실시간으로 조절하기 위해 Redis Hash(ranking:weights)에서 조회한다.
// 키가 없거나 값이 손상된 경우 하드코딩된 RankingWeights.DEFAULT로 폴백해 채점 자체가 멈추지 않게 한다.
@Slf4j
@Component
public class RankingWeightRepositoryImpl implements RankingWeightRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RankingWeightRepositoryImpl(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RankingWeights findCurrent() {
        try {
            Map<Object, Object> raw = redisTemplate.opsForHash().entries(RankingRedisKeys.WEIGHTS_KEY);
            if (raw.isEmpty()) {
                return RankingWeights.DEFAULT;
            }
            return new RankingWeights(
                    parseOrDefault(raw.get("view"), RankingWeights.DEFAULT.view()),
                    parseOrDefault(raw.get("like"), RankingWeights.DEFAULT.like()),
                    parseOrDefault(raw.get("order"), RankingWeights.DEFAULT.order())
            );
        } catch (Exception e) {
            log.warn("랭킹 가중치 조회 실패, 기본값으로 폴백합니다.", e);
            return RankingWeights.DEFAULT;
        }
    }

    private double parseOrDefault(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            log.warn("랭킹 가중치 값 파싱 실패. value={}", value, e);
            return defaultValue;
        }
    }
}
