package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRank;
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * display 보드 읽기 어댑터. 기본(@Primary, REPLICA_PREFERRED) 템플릿을 쓴다 — 쓰는 주체가 다른 앱
 * (streamer)이라 read-after-write 요구가 없고, 서빙 신선도는 어차피 합성 주기에 양자화돼 있어
 * replica lag 수 초는 허용 범위다(대기열이 master 를 쓰는 이유였던 "자기 쓰기 직후 읽기"가 여기엔 없다).
 */
@Component
public class RankingRedisRepository implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RankingRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<RankedProduct> page(LocalDate date, int page, int size) {
        long start = (long) page * size;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(RankingKeys.display(date), start, start + size - 1);
        if (tuples == null) {
            return List.of();
        }
        return tuples.stream()
                .map(tuple -> new RankedProduct(
                        Long.parseLong(String.valueOf(tuple.getValue())),
                        tuple.getScore() == null ? 0.0 : tuple.getScore()))
                .toList();
    }

    @Override
    public long total(LocalDate date) {
        Long size = redisTemplate.opsForZSet().zCard(RankingKeys.display(date));
        return size == null ? 0L : size;
    }

    @Override
    public Optional<ProductRank> rankOf(LocalDate date, long productId) {
        String key = RankingKeys.display(date);
        String member = String.valueOf(productId);
        Long rank = redisTemplate.opsForZSet().reverseRank(key, member);
        if (rank == null) {
            return Optional.empty();
        }
        Double score = redisTemplate.opsForZSet().score(key, member);
        return Optional.of(new ProductRank(rank + 1, score == null ? 0.0 : score));
    }
}
