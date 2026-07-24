package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingProperties;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 랭킹 Redis 어댑터 — 일간 ZSET(member=productId, score=가중 점수).
 * dedup 마커(SETNX)와 점수 반영(ZINCRBY)을 Lua 한 스크립트로 원자화한다.
 * 마커와 부수효과가 같은 저장소이므로 "마킹됐는데 미반영 / 반영됐는데 미마킹"이 구조적으로 불가능하다.
 * 쓰기 경로라 master 템플릿 고정(REPLICA_PREFERRED 금지).
 */
@Component
public class RedisRankingRepository implements RankingRepository {

    // KEYS[1]=dedup 키, KEYS[2]=일간 키 / ARGV[1]=dedup TTL(s), ARGV[2]=일간 TTL(s), ARGV[3..]=(member, delta) 쌍
    private static final String APPLY_ONCE_LUA = """
        if redis.call('SET', KEYS[1], '1', 'NX', 'EX', tonumber(ARGV[1])) then
          for i = 3, #ARGV, 2 do
            redis.call('ZINCRBY', KEYS[2], tonumber(ARGV[i + 1]), ARGV[i])
          end
          redis.call('EXPIRE', KEYS[2], tonumber(ARGV[2]))
          return 1
        end
        return 0
        """;

    private static final RedisScript<Long> APPLY_ONCE = RedisScript.of(APPLY_ONCE_LUA, Long.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final Duration ttl;

    public RedisRankingRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate,
        RankingProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofDays(properties.ttlDays());
    }

    @Override
    public boolean applyOnce(String eventId, LocalDate date, Map<Long, Double> deltas) {
        if (deltas.isEmpty()) {
            return false;
        }
        List<String> keys = List.of(RankingKeys.dedup(eventId), RankingKeys.daily(date));
        List<String> args = new ArrayList<>(2 + deltas.size() * 2);
        args.add(String.valueOf(ttl.toSeconds()));
        args.add(String.valueOf(ttl.toSeconds()));
        deltas.forEach((productId, delta) -> {
            args.add(String.valueOf(productId));
            args.add(String.valueOf(delta));
        });
        Long applied = redisTemplate.execute(APPLY_ONCE, keys, args.toArray());
        return Long.valueOf(1L).equals(applied);
    }

    @Override
    public Optional<Double> findScore(LocalDate date, Long productId) {
        return Optional.ofNullable(
            redisTemplate.opsForZSet().score(RankingKeys.daily(date), String.valueOf(productId)));
    }

    @Override
    public void carryOver(LocalDate from, LocalDate to, double rate) {
        String dest = RankingKeys.daily(to);
        redisTemplate.opsForZSet()
            .unionAndStore(RankingKeys.daily(from), List.of(), dest, Aggregate.SUM, Weights.of(rate));
        redisTemplate.expire(dest, ttl);
    }
}
