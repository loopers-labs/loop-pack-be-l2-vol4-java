package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
        incrementScores(RankingRedisKeys.dailyKey(date), RankingRedisKeys.TTL, productScoreDeltas);
    }

    // 일간 키와 동일한 방식으로 시간 단위 키(ranking:hourly:{yyyyMMddHH})에 반영한다.
    // TTL이 2시간으로 짧아 일간보다 훨씬 자주 만료·재시작되므로 콜드 스타트가 더 빈번하다(Carry-Over가 더 중요해짐).
    @Override
    public void incrementHourlyScores(LocalDateTime dateTime, Map<Long, Double> productScoreDeltas) {
        incrementScores(RankingRedisKeys.hourlyKey(dateTime), RankingRedisKeys.HOURLY_TTL, productScoreDeltas);
    }

    @Override
    public void carryOverScores(LocalDate from, LocalDate to, double ratio) {
        carryOverScores(RankingRedisKeys.dailyKey(from), RankingRedisKeys.dailyKey(to), ratio, RankingRedisKeys.TTL);
    }

    @Override
    public void carryOverHourlyScores(LocalDateTime from, LocalDateTime to, double ratio) {
        carryOverScores(RankingRedisKeys.hourlyKey(from), RankingRedisKeys.hourlyKey(to), ratio, RankingRedisKeys.HOURLY_TTL);
    }

    private void incrementScores(String key, Duration ttl, Map<Long, Double> productScoreDeltas) {
        if (productScoreDeltas.isEmpty()) return;

        productScoreDeltas.forEach((productId, delta) ->
                masterRedisTemplate.opsForZSet().incrementScore(key, String.valueOf(productId), delta)
        );
        masterRedisTemplate.expire(key, ttl);
    }

    // ZUNIONSTORE destKey 2 sourceKey destKey WEIGHTS ratio 1 AGGREGATE SUM
    // destKey를 스스로도 소스에 포함시켜(가중치 1) 이미 있던 값은 보존한 채 sourceKey*ratio만 더한다.
    // sourceKey가 비어있으면(그 시점에 랭킹 데이터가 없으면) union 결과가 비어 destKey는 생성되지 않는다.
    private void carryOverScores(String sourceKey, String destKey, double ratio, Duration ttl) {
        masterRedisTemplate.opsForZSet().unionAndStore(
                sourceKey,
                List.of(destKey),
                destKey,
                Aggregate.SUM,
                Weights.of(ratio, 1.0)
        );
        masterRedisTemplate.expire(destKey, ttl);
    }
}
