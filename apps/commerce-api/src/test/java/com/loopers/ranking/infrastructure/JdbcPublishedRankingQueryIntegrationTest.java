package com.loopers.ranking.infrastructure;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.application.PublishedRanking;
import com.loopers.ranking.application.PublishedRankingQuery;
import com.loopers.ranking.application.RankingPosition;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class JdbcPublishedRankingQueryIntegrationTest {

    private static final LocalDate REQUEST_DATE = LocalDate.of(2026, 7, 15);

    private final PublishedRankingQuery publishedRankingQuery;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    JdbcPublishedRankingQueryIntegrationTest(
        PublishedRankingQuery publishedRankingQuery,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.publishedRankingQuery = publishedRankingQuery;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("집계 종료일과 개정 번호가 가장 큰 완료 스냅샷의 순위를 저장된 순서대로 반환한다.")
    @Test
    void returnsRanksFromLatestCompletedSnapshot() {
        // arrange
        insertSnapshot(1L, RankingPeriod.WEEKLY, "2026-07-13", "2026-07-17", 9, true);
        insertWeeklyRank(1L, 901L, 1);
        updateCompletedAt(1L, LocalDateTime.of(2026, 7, 21, 2, 10));

        insertSnapshot(2L, RankingPeriod.WEEKLY, "2026-07-13", "2026-07-18", 1, true);
        insertWeeklyRank(2L, 201L, 1);

        insertSnapshot(3L, RankingPeriod.WEEKLY, "2026-07-13", "2026-07-18", 2, true);
        insertWeeklyRank(3L, 302L, 4);
        insertWeeklyRank(3L, 301L, 1);
        updateCompletedAt(3L, LocalDateTime.of(2026, 7, 20, 2, 10));

        insertSnapshot(4L, RankingPeriod.WEEKLY, "2026-07-13", "2026-07-18", 3, false);
        insertWeeklyRank(4L, 401L, 1);

        // act
        PublishedRanking result = publishedRankingQuery
            .findLatestCompleted(RankingPeriod.WEEKLY, REQUEST_DATE)
            .orElseThrow();

        // assert
        assertThat(result.positions()).containsExactly(
            new RankingPosition(1, 301L),
            new RankingPosition(4, 302L)
        );
    }

    @DisplayName("요청 날짜의 달력 기간과 기간 시작일이 일치하지 않는 완료 스냅샷은 선택하지 않는다.")
    @Test
    void excludesSnapshotsOutsideRequestedCalendarWindow() {
        // arrange
        insertSnapshot(1L, RankingPeriod.MONTHLY, "2026-06-01", "2026-06-30", 1, true);
        insertSnapshot(2L, RankingPeriod.MONTHLY, "2026-07-02", "2026-07-31", 2, true);
        insertSnapshot(3L, RankingPeriod.MONTHLY, "2026-07-01", "2026-08-01", 3, true);

        // act
        Optional<PublishedRanking> result = publishedRankingQuery.findLatestCompleted(
            RankingPeriod.MONTHLY,
            REQUEST_DATE
        );

        // assert
        assertThat(result).isEmpty();
    }

    @DisplayName("주간과 월간은 각 기간에 대응하는 고정 MV 테이블에서만 순위를 조회한다.")
    @EnumSource(value = RankingPeriod.class, names = {"WEEKLY", "MONTHLY"})
    @ParameterizedTest
    void readsRanksFromTableForPeriod(RankingPeriod period) {
        // arrange
        long snapshotId = 1L;
        String periodStart = period == RankingPeriod.WEEKLY ? "2026-07-13" : "2026-07-01";
        insertSnapshot(snapshotId, period, periodStart, "2026-07-15", 1, true);
        insertRank(tableName(period), snapshotId, 101L, 1);
        insertRank(otherTableName(period), snapshotId, 999L, 1);

        // act
        PublishedRanking result = publishedRankingQuery
            .findLatestCompleted(period, REQUEST_DATE)
            .orElseThrow();

        // assert
        assertThat(result.positions()).containsExactly(new RankingPosition(1, 101L));
    }

    @DisplayName("완료된 빈 스냅샷은 순위가 비어 있는 조회 결과로 구분한다.")
    @Test
    void returnsPresentEmptyRanking_whenCompletedSnapshotHasNoRanks() {
        // arrange
        insertSnapshot(1L, RankingPeriod.WEEKLY, "2026-07-13", "2026-07-15", 1, true);

        // act
        Optional<PublishedRanking> result = publishedRankingQuery.findLatestCompleted(
            RankingPeriod.WEEKLY,
            REQUEST_DATE
        );

        // assert
        assertAll(
            () -> assertThat(result).isPresent(),
            () -> assertThat(result.orElseThrow().positions()).isEmpty()
        );
    }

    @DisplayName("후보를 Cleanup해도 공개된 Ranking 조회 결과는 바뀌지 않는다.")
    @Test
    void returnsSamePublishedRanking_afterCandidatesAreDeleted() {
        // arrange
        insertSnapshot(1L, RankingPeriod.WEEKLY, "2026-07-13", "2026-07-15", 1, true);
        insertWeeklyRank(1L, 101L, 1);
        insertWeeklyRank(1L, 202L, 4);
        insertCandidate(1L, 101L);
        insertCandidate(1L, 202L);
        insertCandidate(1L, 303L);
        PublishedRanking beforeCleanup = publishedRankingQuery
            .findLatestCompleted(RankingPeriod.WEEKLY, REQUEST_DATE)
            .orElseThrow();

        // act
        jdbcTemplate.update(
            """
                delete from product_rank_candidates
                where snapshot_id = ?
                """,
            1L
        );
        PublishedRanking afterCleanup = publishedRankingQuery
            .findLatestCompleted(RankingPeriod.WEEKLY, REQUEST_DATE)
            .orElseThrow();

        // assert
        assertAll(
            () -> assertThat(afterCleanup).isEqualTo(beforeCleanup),
            () -> assertThat(afterCleanup.positions()).containsExactly(
                new RankingPosition(1, 101L),
                new RankingPosition(4, 202L)
            )
        );
    }

    @DisplayName("완료된 스냅샷이 없으면 조회 결과 자체가 비어 있다.")
    @Test
    void returnsEmpty_whenCompletedSnapshotDoesNotExist() {
        // arrange
        insertSnapshot(1L, RankingPeriod.WEEKLY, "2026-07-13", "2026-07-15", 1, false);

        // act
        Optional<PublishedRanking> result = publishedRankingQuery.findLatestCompleted(
            RankingPeriod.WEEKLY,
            REQUEST_DATE
        );

        // assert
        assertThat(result).isEmpty();
    }

    @DisplayName("JDBC 공개 랭킹 조회는 일간 기간을 지원하지 않는다.")
    @Test
    void rejectsDailyPeriod() {
        // act & assert
        assertThatThrownBy(() -> publishedRankingQuery.findLatestCompleted(
            RankingPeriod.DAILY,
            REQUEST_DATE
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("DAILY");
    }

    private void insertSnapshot(
        long id,
        RankingPeriod period,
        String periodStart,
        String aggregationEndDate,
        int revision,
        boolean completed
    ) {
        Timestamp createdAt = Timestamp.valueOf(LocalDateTime.of(2026, 7, 20, 2, 0));
        Timestamp completedAt = completed
            ? Timestamp.valueOf(LocalDateTime.of(2026, 7, 20, 2, 10))
            : null;

        jdbcTemplate.update(
            """
                insert into product_rank_snapshots(
                    id,
                    period,
                    period_start,
                    aggregation_end_date,
                    revision,
                    score_policy_version,
                    view_weight,
                    like_weight,
                    order_weight,
                    order_amount_unit,
                    created_at,
                    completed_at
                )
                values (?, ?, ?, ?, ?, 'V1', 0.1, 0.2, 0.7, 10000, ?, ?)
                """,
            id,
            period.name(),
            periodStart,
            aggregationEndDate,
            revision,
            createdAt,
            completedAt
        );
    }

    private void insertWeeklyRank(long snapshotId, long productId, Integer rank) {
        insertRank("mv_product_rank_weekly", snapshotId, productId, rank);
    }

    private void insertCandidate(long snapshotId, long productId) {
        jdbcTemplate.update(
            """
                insert into product_rank_candidates(
                    snapshot_id,
                    product_id,
                    score
                )
                values (?, ?, 1.0)
                """,
            snapshotId,
            productId
        );
    }

    private void updateCompletedAt(long snapshotId, LocalDateTime completedAt) {
        jdbcTemplate.update(
            "update product_rank_snapshots set completed_at = ? where id = ?",
            Timestamp.valueOf(completedAt),
            snapshotId
        );
    }

    private void insertRank(
        String tableName,
        long snapshotId,
        long productId,
        Integer rank
    ) {
        jdbcTemplate.update(
            "insert into " + tableName
                + "(snapshot_id, product_id, rank_no, score) values (?, ?, ?, 1.0)",
            snapshotId,
            productId,
            rank
        );
    }

    private String tableName(RankingPeriod period) {
        return switch (period) {
            case WEEKLY -> "mv_product_rank_weekly";
            case MONTHLY -> "mv_product_rank_monthly";
            case DAILY -> throw new IllegalArgumentException("DAILY is not supported");
        };
    }

    private String otherTableName(RankingPeriod period) {
        return switch (period) {
            case WEEKLY -> "mv_product_rank_monthly";
            case MONTHLY -> "mv_product_rank_weekly";
            case DAILY -> throw new IllegalArgumentException("DAILY is not supported");
        };
    }
}
