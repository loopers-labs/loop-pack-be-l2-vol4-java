package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.PeriodRankingRepository;
import com.loopers.domain.ranking.RankedProduct;
import com.loopers.domain.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * 기간 랭킹 MV 조회 구현(week10). commerce-batch 가 적재한
 * {@code mv_product_rank_weekly} / {@code mv_product_rank_monthly} 를 읽는다.
 *
 * <p><b>순위를 재계산하지 않는다</b> — 배치가 확정한 {@code rank_no} 를 그대로 쓴다. 조회 시점에
 * {@code ROW_NUMBER()} 를 돌리면 페이지마다 전체 정렬이 필요해 MV 를 둔 의미가 사라진다.
 * {@code (period_start, rank_no)} 인덱스 덕에 페이지 조회는 레인지 스캔 + LIMIT 로 끝난다.
 *
 * <p><b>JPA 엔티티가 아니라 JdbcTemplate 를 쓰는 이유</b>: 주간/월간이 <b>테이블만 다르고 구조가 같아</b>
 * 엔티티로 가면 동일한 쿼리를 두 벌 쓰거나 리포지토리를 둘로 나눠야 한다. 테이블명을 파라미터화하면 한 벌로
 * 끝난다. 엔티티({@code ProductRankWeeklyEntity}/{@code ProductRankMonthlyEntity})는 스키마 정의와
 * 테스트 정리(DatabaseCleanUp)를 위해 유지한다.
 */
@Repository
@RequiredArgsConstructor
public class PeriodRankingMvRepository implements PeriodRankingRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<RankedProduct> findPage(RankingPeriod period, LocalDate periodStart, int page, int size) {
        if (page < 1 || size < 1) {
            return List.of();
        }
        int offset = (page - 1) * size;
        String sql = """
                SELECT rank_no, product_id, score
                  FROM %s
                 WHERE period_start = ?
                 ORDER BY rank_no
                 LIMIT ? OFFSET ?
                """.formatted(tableOf(period));

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new RankedProduct(rs.getLong("rank_no"), rs.getLong("product_id"), rs.getDouble("score")),
                Date.valueOf(periodStart), size, offset);
    }

    @Override
    public long size(RankingPeriod period, LocalDate periodStart) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s WHERE period_start = ?".formatted(tableOf(period)),
                Long.class, Date.valueOf(periodStart));
        return count == null ? 0L : count;
    }

    /** enum → 테이블명. 외부 입력이 아니라 고정 문자열이므로 문자열 조립이 안전하다. */
    private String tableOf(RankingPeriod period) {
        return switch (period) {
            case WEEKLY -> "mv_product_rank_weekly";
            case MONTHLY -> "mv_product_rank_monthly";
            case DAILY -> throw new IllegalArgumentException(
                    "DAILY 는 MV 대상이 아니다 — RankingRepository(ZSET/스냅샷)를 사용할 것");
        };
    }
}
