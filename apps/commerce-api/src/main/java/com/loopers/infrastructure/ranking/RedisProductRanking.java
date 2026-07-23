package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRanking;
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingPeriod;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class RedisProductRanking implements ProductRanking {  // 랭킹 조회 포트의 Redis Sorted Set 어댑터. 근사 지표라 replica 허용(default 템플릿)으로 읽기 오프로드

    static final String KEY_PREFIX = "ranking:all:";

    private final RedisTemplate<String, String> redisTemplate;

    public RedisProductRanking(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<RankedProduct> page(LocalDate date, int page, int size) {
        long start = (long) page * size;
        long end = start + size - 1;
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(keyOf(date), start, end);  // reverseRangeWithScores 는 LinkedHashSet(score 내림차순)이라 순서가 보존된다
        if (tuples == null) {
            return List.of();
        }

        return tuples.stream()
                .map(tuple -> new RankedProduct(
                        Long.valueOf(Objects.requireNonNull(tuple.getValue())),
                        tuple.getScore() == null ? 0.0 : tuple.getScore()
                ))
                .toList();
    }

    @Override
    public long totalCount(LocalDate date) {
        Long count = redisTemplate.opsForZSet().zCard(keyOf(date));
        return count == null ? 0L : count;
    }

    @Override
    public Optional<Long> rankOf(LocalDate date, Long productId) {
        Long rank = redisTemplate.opsForZSet().reverseRank(keyOf(date), String.valueOf(productId));
        return Optional.ofNullable(rank);
    }

    static String keyOf(LocalDate date) {
        return KEY_PREFIX + RankingPeriod.DAILY.periodKey(date);  // 일별 버킷 키(yyyyMMdd) 규칙은 RankingPeriod 가 단일 출처
    }
}
