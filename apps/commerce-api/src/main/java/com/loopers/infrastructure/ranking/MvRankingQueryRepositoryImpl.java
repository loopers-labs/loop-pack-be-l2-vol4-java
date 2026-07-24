package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvRankingQueryRepository;
import com.loopers.domain.ranking.RankedProductEntry;
import com.loopers.domain.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MV 조회 어댑터 — 읽기 전용 projection 이라 엔티티를 두지 않는다.
 * (MV 스키마의 소유자는 이를 생산하는 commerce-batch 다. 조회 앱이 엔티티를 들면 ddl-auto 로
 * 배치 적재분을 날릴 수 있어 의도적으로 매핑하지 않는다.)
 *
 * <p>테이블명은 enum 에서만 결정되므로 SQL 조립이 주입 위험을 만들지 않는다.
 */
@RequiredArgsConstructor
@Component
public class MvRankingQueryRepositoryImpl implements MvRankingQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public long countRanked(RankingPeriod period, String periodKey) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + tableOf(period) + " WHERE period_key = ?", Long.class, periodKey);
        return count != null ? count : 0L;
    }

    @Override
    public List<RankedProductEntry> findPage(RankingPeriod period, String periodKey, int offset, int size) {
        return jdbcTemplate.query(
            "SELECT product_id, score FROM " + tableOf(period)
                + " WHERE period_key = ? ORDER BY rank_no ASC LIMIT ? OFFSET ?",
            (rs, rowNum) -> new RankedProductEntry(rs.getLong("product_id"), rs.getDouble("score")),
            periodKey, size, offset);
    }

    private static String tableOf(RankingPeriod period) {
        return switch (period) {
            case WEEKLY -> "mv_product_rank_weekly";
            case MONTHLY -> "mv_product_rank_monthly";
            case DAILY -> throw new IllegalArgumentException(
                "일간 랭킹은 MV 가 아닌 실시간 ZSET 에서 조회합니다.");
        };
    }
}
