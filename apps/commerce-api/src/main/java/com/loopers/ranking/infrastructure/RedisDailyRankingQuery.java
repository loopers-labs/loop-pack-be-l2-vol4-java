package com.loopers.ranking.infrastructure;

import com.loopers.ranking.RankingRedisKey;
import com.loopers.ranking.application.DailyRankingEntries;
import com.loopers.ranking.application.DailyRankingQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class RedisDailyRankingQuery implements DailyRankingQuery {

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public DailyRankingEntries findDaily(LocalDate date, long start, long end) {
        String key = RankingRedisKey.daily(date);
        Set<String> members = redisTemplate.opsForZSet().reverseRange(key, start, end);
        Long totalElements = redisTemplate.opsForZSet().zCard(key);

        return new DailyRankingEntries(toProductIds(members), totalElements == null ? 0 : totalElements);
    }

    @Override
    public Optional<Long> findDailyRank(LocalDate date, Long productId) {
        Long position = redisTemplate.opsForZSet().reverseRank(
            RankingRedisKey.daily(date),
            String.valueOf(productId)
        );
        return Optional.ofNullable(position);
    }

    private List<Long> toProductIds(Set<String> members) {
        if (members == null) {
            return List.of();
        }
        return members.stream()
            .map(Long::valueOf)
            .toList();
    }
}
