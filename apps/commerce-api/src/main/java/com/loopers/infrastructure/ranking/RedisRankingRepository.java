package com.loopers.infrastructure.ranking;

import com.loopers.application.ranking.RankedProduct;
import com.loopers.application.ranking.RankingRepository;
import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.DailyRankingKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

@Component
public class RedisRankingRepository implements RankingRepository {
    private final RedisTemplate<String, String> redisTemplate;

    public RedisRankingRepository(
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<RankedProduct> findRankedProducts(LocalDate date, int page, int size) {
        long start = (long) (page - 1) * size;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
            .reverseRangeByScoreWithScores(
                DailyRankingKey.from(date),
                Double.MIN_VALUE,
                Double.MAX_VALUE,
                start,
                size
            );
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<RankedProduct> rankings = new ArrayList<>(tuples.size());
        long rank = start + 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() != null && tuple.getScore() != null) {
                rankings.add(new RankedProduct(rank, Long.valueOf(tuple.getValue()), tuple.getScore()));
            }
            rank++;
        }
        return rankings;
    }

    @Override
    public OptionalLong findRank(LocalDate date, Long productId) {
        String key = DailyRankingKey.from(date);
        String member = DailyRankingKey.member(productId);
        Double score = redisTemplate.opsForZSet().score(key, member);
        if (score == null || score <= 0.0) {
            return OptionalLong.empty();
        }

        Long zeroBasedRank = redisTemplate.opsForZSet()
            .reverseRank(key, member);
        return zeroBasedRank == null ? OptionalLong.empty() : OptionalLong.of(zeroBasedRank + 1);
    }
}
