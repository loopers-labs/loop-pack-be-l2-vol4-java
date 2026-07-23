package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingSignal;
import com.loopers.domain.ranking.RankingSlot;
import com.loopers.support.config.RankingProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link RankingRepository} 의 Redis ZSET 어댑터. 쓰기 전용이므로 master 템플릿을 고정 사용한다
 * (읽기 서빙은 commerce-api 쪽 어댑터가 replica 로 수행).
 *
 * <p>Redis 예외는 <b>전파한다</b>(삼킴 없음) — 랭킹 consumer 는 전용 그룹이라 실패 시 offset 미커밋
 * 재시도가 곧 무손실 버퍼링이다. 실패를 숨기면 그 장치가 무력화된다.</p>
 *
 * <p>TTL 은 매 쓰기마다 갱신한다: ZINCRBY/ZUNIONSTORE 는 키를 새로 만들 때 TTL 을 붙이지 않으므로
 * "첫 쓰기 판별" 대신 무조건 EXPIRE 를 친다. 날짜 스코프 키라 그 날짜가 지나면 쓰기가 끊겨
 * TTL 밀림은 실질 무해(청소 목적)이고, 왕복 1회 추가는 collector 처리량에서 무시 가능.</p>
 */
@Component
public class RankingRedisRepository implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;
    private final Duration ttl;

    public RankingRedisRepository(
            @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
            RankingProperties rankingProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofDays(rankingProperties.ttlDays());
    }

    @Override
    public void incrementAll(Map<RankingSlot, Double> deltas) {
        if (deltas.isEmpty()) {
            return;
        }
        Set<String> touchedKeys = deltas.keySet().stream()
                .map(slot -> RankingKeys.raw(slot.signal(), slot.date()))
                .collect(Collectors.toSet());
        // 파이프라인 = 왕복 1회에 ZINCRBY N개 + 키당 EXPIRE 1개. 트랜잭션(MULTI)이 아니므로 원자성은 없다
        // — 도중 단절 시 부분 반영 + 배치 재시도 이중 가산은 포트 계약대로 근사 예산.
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Map.Entry<RankingSlot, Double> entry : deltas.entrySet()) {
                RankingSlot slot = entry.getKey();
                connection.zSetCommands().zIncrBy(
                        RankingKeys.raw(slot.signal(), slot.date()).getBytes(StandardCharsets.UTF_8),
                        entry.getValue(),
                        String.valueOf(slot.productId()).getBytes(StandardCharsets.UTF_8));
            }
            for (String key : touchedKeys) {
                connection.keyCommands().expire(key.getBytes(StandardCharsets.UTF_8), ttl.toSeconds());
            }
            return null;
        });
    }

    @Override
    public void compose(LocalDate date, Map<RankingSignal, Double> weights) {
        // ZUNIONSTORE 는 destination 을 통째로 덮어쓴다 → "현재 raw × 현재 가중치" 전체 재계산이라
        // 재실행·다중 인스턴스 동시 실행 모두 같은 결과(멱등). 가중치 변경은 다음 합성부터 당일 전체에 소급된다.
        List<String> rawKeys = new ArrayList<>();
        double[] rawWeights = new double[RankingSignal.values().length];
        int i = 0;
        for (RankingSignal signal : RankingSignal.values()) {
            rawKeys.add(RankingKeys.raw(signal, date));
            rawWeights[i++] = weights.getOrDefault(signal, 0.0);
        }

        String displayKey = RankingKeys.display(date);
        redisTemplate.opsForZSet().unionAndStore(
                rawKeys.get(0),
                rawKeys.subList(1, rawKeys.size()),
                displayKey,
                Aggregate.SUM,
                Weights.of(rawWeights)
        );
        redisTemplate.expire(displayKey, ttl);
    }

    @Override
    public boolean carryOver(LocalDate from, LocalDate to, double weight) {
        // 시드는 자기 자신(to)을 소스에 포함하는 합산이라 재실행 시 시드가 중복 가산된다(비멱등)
        // → SET NX 마커로 to 날짜당 1회를 보장한다. 마커는 다중 인스턴스의 동시 실행 가드를 겸한다.
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(RankingKeys.carryOverMarker(to), "done", ttl);
        if (!Boolean.TRUE.equals(acquired)) {
            return false;
        }

        for (RankingSignal signal : RankingSignal.values()) {
            String toKey = RankingKeys.raw(signal, to);
            String fromKey = RankingKeys.raw(signal, from);
            // 대상(to) weight=1.0 으로 이미 쌓인 증분을 보존한다 — from 만 소스로 두면 destination
            // 덮어쓰기가 to 보드의 기존 점수를 지운다. from 키 부재 시 자연 no-op.
            redisTemplate.opsForZSet().unionAndStore(
                    toKey, List.of(fromKey), toKey, Aggregate.SUM, Weights.of(1.0, weight));
            redisTemplate.expire(toKey, ttl);
        }
        return true;
    }
}
