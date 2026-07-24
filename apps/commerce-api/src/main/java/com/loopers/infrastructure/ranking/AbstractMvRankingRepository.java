package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 배치가 적재한 랭킹 MV(mv_product_rank_weekly/monthly)를 조회하는 공통 로직. 주간/월간은 테이블 이름만 달라 여기 모은다.
 * 조회는 무거운 집계 없이 "이미 매겨진 rank_no 로 읽기 + 페이지 자르기"뿐이다 (week10 qna Q2).
 *
 * 스냅샷 폴백: 요청일의 스냅샷이 아직 없으면(배치 전) 그 이전의 가장 최근 스냅샷으로 조회한다 — stale 이 empty 보다 낫다 (일간의 어제-폴백과 같은 정책, Q3/D8).
 * MV 는 api 가 읽기만 하므로 엔티티 없이 JdbcTemplate 으로 직접 조회한다 (RankingRedisRepository 가 RedisTemplate 을 쓰는 것과 같은 결).
 */
public abstract class AbstractMvRankingRepository implements RankingRepository {

    private final JdbcTemplate jdbcTemplate;

    protected AbstractMvRankingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 조회할 MV 테이블 이름. 하드코딩 상수이므로 SQL 문자열에 직접 넣어도 주입 위험이 없다. */
    protected abstract String tableName();

    @Override
    public List<Long> findProductIds(LocalDate date, int page, int size) {
        long offset = (long) (page - 1) * size;
        String sql = "SELECT product_id FROM " + tableName()
            + " WHERE snapshot_date = " + latestSnapshotOnOrBefore()
            + " ORDER BY rank_no LIMIT ? OFFSET ?";
        return jdbcTemplate.queryForList(sql, Long.class, date, size, offset);
    }

    @Override
    public Optional<Long> findRank(LocalDate date, Long productId) {
        // 계약상 0-based(Redis ZREVRANK 와 동일) — MV 의 rank_no 는 1-based 라 1 을 뺀다. Facade 가 표시용으로 +1 한다.
        String sql = "SELECT rank_no - 1 FROM " + tableName()
            + " WHERE snapshot_date = " + latestSnapshotOnOrBefore() + " AND product_id = ?";
        return jdbcTemplate.queryForList(sql, Long.class, date, productId).stream().findFirst();
    }

    @Override
    public long count(LocalDate date) {
        String sql = "SELECT COUNT(*) FROM " + tableName()
            + " WHERE snapshot_date = " + latestSnapshotOnOrBefore();
        Long count = jdbcTemplate.queryForObject(sql, Long.class, date);
        return count == null ? 0L : count;
    }

    /** "요청일 이하의 가장 최근 스냅샷" 서브쿼리. 파라미터(?)로 date 를 받는다. */
    private String latestSnapshotOnOrBefore() {
        return "(SELECT MAX(snapshot_date) FROM " + tableName() + " WHERE snapshot_date <= ?)";
    }
}
