package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 랭킹은 조회가 매우 잦고 약간의 복제 지연을 감수해도 되는 파생 뷰라, replica 우선 읽기인
 * 기본(@Primary) 템플릿을 쓴다 — 대기열(OrderQueueRedisRepository)의 master 고정과는 다른 선택 (week9 qna 참고).
 */
@RequiredArgsConstructor
@Component
public class RankingRedisRepository implements RankingRepository {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public RankingPeriod period() {
        return RankingPeriod.DAILY;
    }

    @Override
    public List<Long> findProductIds(LocalDate date, int page, int size) {
        String key = RankingKeys.of(date);
        long start = (long) (page - 1) * size;
        long stop = (long) page * size - 1;
        // reverseRange 는 Set<String> 을 반환하지만, Spring Data Redis 구현은 Redis 응답 순서를
        // 보존하는 LinkedHashSet 을 쓰므로 순위 순서 그대로 읽을 수 있다.
        Set<String> members = redisTemplate.opsForZSet().reverseRange(key, start, stop);
        if (members == null) {
            return List.of();
        }
        return members.stream().map(Long::valueOf).toList();
    }

    @Override
    public Optional<Long> findRank(LocalDate date, Long productId) {
        String key = RankingKeys.of(date);
        Long rank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
        return Optional.ofNullable(rank);
    }

    @Override
    public long count(LocalDate date) {
        String key = RankingKeys.of(date);
        Long count = redisTemplate.opsForZSet().zCard(key);
        return count == null ? 0L : count;
    }
}
