package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
public class RankingRedisRepository implements RankingRepository {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter KEY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRedisRepository(RedisTemplate<String, String> defaultRedisTemplate) {
        this.redisTemplate = defaultRedisTemplate;
    }

    @Override
    public List<RankingEntry> getRankings(LocalDate date, int page, int size) {
        String key = buildKey(date);
        long start = (long) (page - 1) * size;
        long end = start + size - 1;

        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
            .reverseRangeWithScores(key, start, end);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<RankingEntry> entries = new ArrayList<>();
        long rank = start + 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            entries.add(new RankingEntry(Long.valueOf(tuple.getValue()), rank++, tuple.getScore()));
        }
        return entries;
    }

    @Override
    public Long getRank(LocalDate date, Long productId) {
        String key = buildKey(date);
        Long reverseRank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
        return reverseRank == null ? null : reverseRank + 1;
    }

    private String buildKey(LocalDate date) {
        return KEY_PREFIX + date.format(KEY_DATE_FORMAT);
    }
}
