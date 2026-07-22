package com.loopers.ranking.infrastructure;

import com.loopers.ranking.domain.RankingEntry;
import com.loopers.ranking.domain.RankingRepository;
import com.loopers.ranking.domain.RankingScoreDelta;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 랭킹 ZSET 적재 어댑터. fold 된 delta 들을 pipeline 으로 한 번에 ZINCRBY 하고,
 * 날짜별 키에 절대 만료(date + 2일)를 건다 — 반복 적용해도 sliding 되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RedisRankingRepository implements RankingRepository {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int TTL_DAYS = 2;

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void incrBy(List<RankingScoreDelta> deltas) {
        if (deltas.isEmpty()) {
            return;
        }
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @SuppressWarnings({"unchecked", "rawtypes"})
            @Override
            public Object execute(RedisOperations operations) {
                for (RankingScoreDelta delta : deltas) {
                    operations.opsForZSet().incrementScore(key(delta.date()), member(delta.productId()), delta.score());
                }
                for (LocalDate date : distinctDates(deltas)) {
                    operations.expireAt(key(date), expireAt(date));
                }
                return null;
            }
        });
    }

    @Override
    public void carryOver(LocalDate from, LocalDate to, double weight) {
        redisTemplate.opsForZSet().unionAndStore(
                key(from), Collections.emptyList(), key(to), Aggregate.SUM, Weights.of(weight));
        redisTemplate.expireAt(key(to), expireAt(to));
    }

    @Override
    public List<RankingEntry> readDesc(LocalDate date) {
        Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key(date), 0, -1);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        return tuples.stream()
                .map(t -> new RankingEntry(Long.parseLong(t.getValue()), t.getScore() == null ? 0.0 : t.getScore()))
                .toList();
    }

    @Override
    public void rebuild(LocalDate date, List<RankingEntry> seeds) {
        if (seeds.isEmpty()) {
            return;
        }
        String tempKey = key(date) + ":rebuild";
        redisTemplate.delete(tempKey);
        for (RankingEntry seed : seeds) {
            redisTemplate.opsForZSet().add(tempKey, member(seed.productId()), seed.score());
        }
        redisTemplate.expireAt(tempKey, expireAt(date));
        redisTemplate.rename(tempKey, key(date));
    }

    private Set<LocalDate> distinctDates(List<RankingScoreDelta> deltas) {
        return deltas.stream().map(RankingScoreDelta::date).collect(Collectors.toSet());
    }

    private String key(LocalDate date) {
        return KEY_PREFIX + date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private String member(long productId) {
        return String.valueOf(productId);
    }

    private Instant expireAt(LocalDate date) {
        return date.plusDays(TTL_DAYS).atStartOfDay(SEOUL).toInstant();
    }
}
