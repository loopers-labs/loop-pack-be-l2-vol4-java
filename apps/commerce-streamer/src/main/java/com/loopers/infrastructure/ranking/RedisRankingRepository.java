package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * 일간 랭킹 ZSET Redis 구현. 적재(쓰기)는 복제 지연이 없는 master 템플릿으로 보낸다.
 * <p>
 * 월요일: 계약/뼈대만. 실제 ZINCRBY/ZREVRANGE/ZREVRANK 구현과 Testcontainers 검증은 화요일에 진행한다.
 */
@Component
public class RedisRankingRepository implements RankingRepository {

    private static final Duration TTL = Duration.ofDays(2);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisRankingRepository(
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void incrScore(LocalDate date, Long productId, double delta) {
        // TODO(화): RankingKey.daily(date) 키에 opsForZSet().incrementScore(...) + expire(TTL)
        throw new UnsupportedOperationException("incrScore: 화요일 test-first 구현 예정");
    }

    @Override
    public List<RankedProduct> topN(LocalDate date, long offset, long count) {
        // TODO(수): reverseRangeWithScores(key, offset, offset + count - 1)
        throw new UnsupportedOperationException("topN: 수요일 구현 예정");
    }

    @Override
    public Long findRank(LocalDate date, Long productId) {
        // TODO(목): reverseRank(key, productId) → null이면 그대로, 있으면 +1(1-based)
        throw new UnsupportedOperationException("findRank: 목요일 구현 예정");
    }
}
