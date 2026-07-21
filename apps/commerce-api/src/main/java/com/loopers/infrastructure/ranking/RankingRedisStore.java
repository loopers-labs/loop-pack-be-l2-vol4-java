package com.loopers.infrastructure.ranking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 실시간 상품 랭킹 ZSET 조회소 (읽기 측 — commerce-api).
 *
 * <p>쓰기(집계/ZINCRBY)는 streamer 가 담당하고, 여기서는 라이브 읽기만 한다.
 * 랭킹은 항상 최신값을 보여야 하므로 상품 상세 캐시({@code product:detail})와 분리해 매 요청 조회한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RankingRedisStore {

    private static final String ALL_KEY_PREFIX = "ranking:all:";
    private static final String HOUR_KEY_PREFIX = "ranking:hour:";
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 일간 랭킹에서 {@code [start, end]} 구간의 상품 ID 를 점수 내림차순으로 반환한다(ZREVRANGE).
     * 구간이 비었거나 Redis 장애면 빈 리스트.
     */
    public List<Long> findProductIdsByRank(LocalDate date, long start, long end) {
        return findProductIdsByKey(ALL_KEY_PREFIX + date.format(DAY_FMT), start, end);
    }

    /**
     * 시간별 랭킹에서 {@code [start, end]} 구간의 상품 ID 를 점수 내림차순으로 반환한다(ZREVRANGE).
     * carry-over 가 없어 매 시간 0점부터 시작하는 게 의도된 동작이다.
     */
    public List<Long> findProductIdsByHour(LocalDateTime hour, long start, long end) {
        return findProductIdsByKey(HOUR_KEY_PREFIX + hour.format(HOUR_FMT), start, end);
    }

    private List<Long> findProductIdsByKey(String key, long start, long end) {
        if (start > end) {
            return List.of();
        }
        try {
            Set<String> members = redisTemplate.opsForZSet().reverseRange(key, start, end);
            if (members == null || members.isEmpty()) {
                return List.of();
            }
            List<Long> ids = new ArrayList<>(members.size());
            for (String member : members) {
                ids.add(Long.valueOf(member));
            }
            return ids;
        } catch (Exception e) {
            log.warn("[Ranking] ZREVRANGE 실패 — 빈 랭킹으로 폴백. key={}, start={}, end={}", key, start, end, e);
            return List.of();
        }
    }

    /**
     * 오늘 일간 랭킹에서 상품의 라이브 순위(1-indexed)를 반환한다(ZREVRANK). 랭킹에 없으면 null.
     * 상세 응답에 병합할 실시간 값 — 캐시하지 않는다.
     */
    public Integer findLiveRank(Long productId) {
        try {
            Long rank = redisTemplate.opsForZSet()
                .reverseRank(ALL_KEY_PREFIX + LocalDate.now().format(DAY_FMT), String.valueOf(productId));
            return rank == null ? null : (int) (rank + 1);   // 0-indexed → 1-indexed
        } catch (Exception e) {
            log.warn("[Ranking] ZREVRANK 실패 — rank 없음으로 폴백. productId={}", productId, e);
            return null;
        }
    }
}
