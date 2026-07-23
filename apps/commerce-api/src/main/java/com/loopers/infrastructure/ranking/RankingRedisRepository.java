package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@link RankingRepository} 의 Redis ZSET 구현(읽기 전용). 내림차순(reverse) 조회로 상위 랭킹을 가져온다.
 * 값 직렬화는 String 이라 member(productId)/score 를 문자열·double 로 다룬다.
 */
@Repository
@RequiredArgsConstructor
public class RankingRedisRepository implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public List<RankedProduct> findPage(LocalDate date, int page, int size) {
        if (page < 1 || size < 1) {
            return List.of();
        }
        String key = RankingKey.daily(date);
        long start = (long) (page - 1) * size;  // 0-based offset
        long end = start + size - 1;

        Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<RankedProduct> result = new ArrayList<>(tuples.size());
        long rank = start + 1; // 1-based 순위
        for (TypedTuple<String> tuple : tuples) { // reverseRange 는 순서(내림차순)를 보존한다
            String member = tuple.getValue();
            Double score = tuple.getScore();
            if (member == null) {
                continue;
            }
            result.add(new RankedProduct(rank++, Long.valueOf(member), score == null ? 0.0 : score));
        }
        return result;
    }

    @Override
    public long size(LocalDate date) {
        Long card = redisTemplate.opsForZSet().zCard(RankingKey.daily(date));
        return card == null ? 0L : card;
    }

    @Override
    public Optional<Long> findRank(LocalDate date, Long productId) {
        if (productId == null) {
            return Optional.empty();
        }
        Long reverseRank = redisTemplate.opsForZSet()
                .reverseRank(RankingKey.daily(date), String.valueOf(productId));
        return reverseRank == null ? Optional.empty() : Optional.of(reverseRank + 1); // 0-based → 1-based
    }
}
