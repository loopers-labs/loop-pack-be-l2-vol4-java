package com.loopers.ranking.infrastructure;

import com.loopers.ranking.domain.RankingEntry;
import com.loopers.ranking.domain.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 랭킹 ZSET 조회 어댑터. 날짜로 키를 만들어 ZREVRANGE/ZCARD/ZREVRANK 로 읽는다.
 */
@Component
@RequiredArgsConstructor
public class RedisRankingRepository implements RankingRepository {

    private static final String KEY_PREFIX = "ranking:all:";

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public List<RankingEntry> range(LocalDate date, long start, long end) {
        Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key(date), start, end);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        return tuples.stream()
                .map(t -> new RankingEntry(Long.parseLong(t.getValue()), t.getScore() == null ? 0.0 : t.getScore()))
                .toList();
    }

    @Override
    public long size(LocalDate date) {
        Long count = redisTemplate.opsForZSet().zCard(key(date));
        return count == null ? 0L : count;
    }

    @Override
    public Long rank(LocalDate date, long productId) {
        return redisTemplate.opsForZSet().reverseRank(key(date), String.valueOf(productId));
    }

    private String key(LocalDate date) {
        return KEY_PREFIX + date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
