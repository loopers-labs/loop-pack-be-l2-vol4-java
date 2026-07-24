package com.loopers.ranking.infrastructure;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.application.NewProductRankingSnapshot;
import com.loopers.ranking.application.ProductRankingSnapshotHeader;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
class JdbcProductRankingSnapshotRepositoryIntegrationTest {

    private static final ProductRankingSnapshotKey SNAPSHOT_KEY = new ProductRankingSnapshotKey(
        RankingPeriod.WEEKLY,
        LocalDate.of(2026, 7, 19),
        1
    );
    private static final RankingScorePolicy SCORE_POLICY =
        new RankingScorePolicy(0.1, 0.2, 0.7, 10_000);
    private static final Instant CREATED_AT = Instant.parse("2026-07-20T02:00:00.123456Z");

    private final JdbcProductRankingSnapshotRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    JdbcProductRankingSnapshotRepositoryIntegrationTest(
        JdbcProductRankingSnapshotRepository repository,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("미완성 Snapshot을 저장한 뒤 비즈니스 키로 정책과 UTC 시각을 복원한다.")
    @Test
    void insertsAndFindsIncompleteSnapshot() {
        // arrange
        NewProductRankingSnapshot snapshot =
            new NewProductRankingSnapshot(SNAPSHOT_KEY, SCORE_POLICY, CREATED_AT);

        // act
        repository.insert(snapshot);
        ProductRankingSnapshotHeader result = repository.findBy(SNAPSHOT_KEY).orElseThrow();

        // assert
        LocalDate periodStart = jdbcTemplate.queryForObject(
            "select period_start from product_rank_snapshots where id = ?",
            LocalDate.class,
            result.id()
        );
        assertAll(
            () -> assertThat(result.id()).isPositive(),
            () -> assertThat(result.key()).isEqualTo(SNAPSHOT_KEY),
            () -> assertThat(result.scorePolicy().version()).isEqualTo("V1"),
            () -> assertThat(result.scorePolicy().viewWeight()).isEqualTo(0.1),
            () -> assertThat(result.scorePolicy().likeWeight()).isEqualTo(0.2),
            () -> assertThat(result.scorePolicy().orderWeight()).isEqualTo(0.7),
            () -> assertThat(result.scorePolicy().orderAmountUnit()).isEqualTo(10_000),
            () -> assertThat(result.createdAt()).isEqualTo(CREATED_AT),
            () -> assertThat(result.completedAt()).isNull(),
            () -> assertThat(periodStart).isEqualTo(LocalDate.of(2026, 7, 13))
        );
    }

    @DisplayName("존재하지 않는 비즈니스 키를 조회하면 빈 결과를 반환한다.")
    @Test
    void returnsEmpty_whenSnapshotDoesNotExist() {
        // act & assert
        assertThat(repository.findBy(SNAPSHOT_KEY)).isEmpty();
    }

    @DisplayName("저장된 Snapshot을 ID로 조회한다.")
    @Test
    void findsSnapshotById() {
        // arrange
        repository.insert(new NewProductRankingSnapshot(
            SNAPSHOT_KEY,
            SCORE_POLICY,
            CREATED_AT
        ));
        long snapshotId = repository.findBy(SNAPSHOT_KEY).orElseThrow().id();

        // act
        ProductRankingSnapshotHeader result =
            repository.findById(snapshotId).orElseThrow();

        // assert
        assertThat(result.key()).isEqualTo(SNAPSHOT_KEY);
    }

    @DisplayName("같은 기간에서 집계 종료일이 더 최신인 완료 Snapshot을 찾는다.")
    @Test
    void findsCompletedSnapshotWithNewerAggregationEndDate() {
        // arrange
        ProductRankingSnapshotKey newerKey = new ProductRankingSnapshotKey(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 26),
            1
        );
        insertCompletedSnapshot(newerKey);

        // act
        boolean result = repository.existsNewerCompletedThan(SNAPSHOT_KEY);

        // assert
        assertThat(result).isTrue();
    }

    @DisplayName("같은 집계 종료일에서 revision이 더 높은 완료 Snapshot을 찾는다.")
    @Test
    void findsCompletedSnapshotWithHigherRevision() {
        // arrange
        ProductRankingSnapshotKey newerRevisionKey = new ProductRankingSnapshotKey(
            RankingPeriod.WEEKLY,
            SNAPSHOT_KEY.aggregationEndDate(),
            2
        );
        insertCompletedSnapshot(newerRevisionKey);

        // act
        boolean result = repository.existsNewerCompletedThan(SNAPSHOT_KEY);

        // assert
        assertThat(result).isTrue();
    }

    @DisplayName("더 높은 revision이 미완성이거나 다른 기간이면 새로운 완료본으로 보지 않는다.")
    @Test
    void ignoresIncompleteAndDifferentPeriodSnapshots() {
        // arrange
        repository.insert(new NewProductRankingSnapshot(
            new ProductRankingSnapshotKey(
                RankingPeriod.WEEKLY,
                SNAPSHOT_KEY.aggregationEndDate(),
                2
            ),
            SCORE_POLICY,
            CREATED_AT
        ));
        insertCompletedSnapshot(new ProductRankingSnapshotKey(
            RankingPeriod.MONTHLY,
            LocalDate.of(2026, 7, 31),
            1
        ));

        // act
        boolean result = repository.existsNewerCompletedThan(SNAPSHOT_KEY);

        // assert
        assertThat(result).isFalse();
    }

    @DisplayName("과거 집계 종료일을 나중에 완료해도 더 새로운 Snapshot으로 보지 않는다.")
    @Test
    void ignoresOlderAggregationEndDateCompletedLater() {
        // arrange
        insertCompletedSnapshot(
            new ProductRankingSnapshotKey(
                RankingPeriod.WEEKLY,
                LocalDate.of(2026, 7, 12),
                99
            ),
            Instant.parse("2026-07-21T02:10:00Z")
        );

        // act
        boolean result = repository.existsNewerCompletedThan(SNAPSHOT_KEY);

        // assert
        assertThat(result).isFalse();
    }

    @DisplayName("저장된 점수 정책 버전을 현재 코드가 지원하지 않으면 조회에 실패한다.")
    @Test
    void rejectsUnsupportedStoredPolicyVersion() {
        // arrange
        jdbcTemplate.update(
            """
                insert into product_rank_snapshots(
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
                values ('WEEKLY', '2026-07-13', '2026-07-19', 1, 'V2', 0.1, 0.2, 0.7, 10000, ?, null)
                """,
            LocalDateTime.of(2026, 7, 20, 2, 0)
        );

        // act & assert
        assertThatThrownBy(() -> repository.findBy(SNAPSHOT_KEY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("V2");
    }

    @DisplayName("미완성 Snapshot은 한 번만 완료 상태로 전환한다.")
    @Test
    void completesIncompleteSnapshotOnlyOnce() {
        // arrange
        repository.insert(new NewProductRankingSnapshot(
            SNAPSHOT_KEY,
            SCORE_POLICY,
            CREATED_AT
        ));
        ProductRankingSnapshotHeader snapshot = repository.findBy(SNAPSHOT_KEY).orElseThrow();
        Instant completedAt = Instant.parse("2026-07-20T02:10:00.654321Z");

        // act
        boolean firstResult = repository.completeIfIncomplete(snapshot.id(), completedAt);
        boolean secondResult = repository.completeIfIncomplete(snapshot.id(), completedAt);

        // assert
        ProductRankingSnapshotHeader completed = repository.findBy(SNAPSHOT_KEY).orElseThrow();
        assertAll(
            () -> assertThat(firstResult).isTrue(),
            () -> assertThat(secondResult).isFalse(),
            () -> assertThat(completed.completedAt()).isEqualTo(completedAt)
        );
    }

    private void insertCompletedSnapshot(ProductRankingSnapshotKey key) {
        insertCompletedSnapshot(
            key,
            Instant.parse("2026-07-20T02:10:00Z")
        );
    }

    private void insertCompletedSnapshot(
        ProductRankingSnapshotKey key,
        Instant completedAt
    ) {
        repository.insert(new NewProductRankingSnapshot(
            key,
            SCORE_POLICY,
            CREATED_AT
        ));
        ProductRankingSnapshotHeader snapshot = repository.findBy(key).orElseThrow();
        repository.completeIfIncomplete(snapshot.id(), completedAt);
    }
}
