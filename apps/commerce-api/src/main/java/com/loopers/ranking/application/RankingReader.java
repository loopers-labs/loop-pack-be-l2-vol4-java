package com.loopers.ranking.application;

import com.loopers.ranking.domain.RankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 다른 도메인(상품 상세)이 오늘 순위만 얻기 위한 좁은 read 진입점.
 * 1-based 순위를 돌려주고, 랭킹에 없거나 Redis 장애면 null 이다(상세 조회 자체를 막지 않는다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingReader {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RankingRepository rankingRepository;

    public Integer todayRank(long productId) {
        try {
            Long rank = rankingRepository.rank(LocalDate.now(SEOUL), productId);
            return rank == null ? null : (int) (rank + 1);
        } catch (DataAccessException e) {
            log.warn("ranking rank read failed reason=redis_error productId={}", productId, e);
            return null;
        }
    }
}
