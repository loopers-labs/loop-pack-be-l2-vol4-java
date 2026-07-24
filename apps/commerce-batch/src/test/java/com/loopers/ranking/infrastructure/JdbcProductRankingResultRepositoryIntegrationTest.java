package com.loopers.ranking.infrastructure;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.application.NewProductRankingSnapshot;
import com.loopers.ranking.application.ProductRankingAssignment;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
class JdbcProductRankingResultRepositoryIntegrationTest {

    private static final RankingScorePolicy SCORE_POLICY =
        new RankingScorePolicy(0.1, 0.2, 0.7, 10_000);

    private final JdbcProductRankingResultRepository resultRepository;
    private final JdbcProductRankingSnapshotRepository snapshotRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    JdbcProductRankingResultRepositoryIntegrationTest(
        JdbcProductRankingResultRepository resultRepository,
        JdbcProductRankingSnapshotRepository snapshotRepository,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.resultRepository = resultRepository;
        this.snapshotRepository = snapshotRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("기간에 맞는 공개 MV에 순위 결과만 저장하고 순위 순으로 조회한다.")
    @EnumSource(value = RankingPeriod.class, names = {"WEEKLY", "MONTHLY"})
    @ParameterizedTest
    void insertsAndFindsPublishedRankings(RankingPeriod period) {
        // arrange
        long snapshotId = insertSnapshot(period);
        List<ProductRankingAssignment> rankings = List.of(
            new ProductRankingAssignment(303L, 10.0, 1),
            new ProductRankingAssignment(401L, 5.3, 2),
            new ProductRankingAssignment(402L, 5.3, 3)
        );

        // act
        resultRepository.insertAll(period, snapshotId, rankings);
        List<ProductRankingAssignment> result =
            resultRepository.findPublishedRankings(period, snapshotId, 101);

        // assert
        assertAll(
            () -> assertThat(result).containsExactlyElementsOf(rankings),
            () -> assertThat(countRows(otherTableName(period))).isZero()
        );
    }

    @DisplayName("같은 Snapshot에 동일한 실제 순위를 중복 저장할 수 없다.")
    @Test
    void rejectsDuplicateRankNumber() {
        // arrange
        RankingPeriod period = RankingPeriod.WEEKLY;
        long snapshotId = insertSnapshot(period);
        resultRepository.insertAll(
            period,
            snapshotId,
            List.of(new ProductRankingAssignment(101L, 10.0, 1))
        );

        // act & assert
        assertThatThrownBy(() -> resultRepository.insertAll(
            period,
            snapshotId,
            List.of(new ProductRankingAssignment(202L, 7.0, 1))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("DAILY 공개 MV는 지원하지 않는다.")
    @Test
    void rejectsDailyPeriod() {
        // act & assert
        assertAll(
            () -> assertThatThrownBy(() -> resultRepository.insertAll(
                RankingPeriod.DAILY,
                1L,
                List.of()
            ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DAILY"),
            () -> assertThatThrownBy(() ->
                resultRepository.findPublishedRankings(
                    RankingPeriod.DAILY,
                    1L,
                    101
                )
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DAILY")
        );
    }

    private long insertSnapshot(RankingPeriod period) {
        ProductRankingSnapshotKey key = new ProductRankingSnapshotKey(
            period,
            LocalDate.of(2026, 7, 19),
            1
        );
        snapshotRepository.insert(
            new NewProductRankingSnapshot(
                key,
                SCORE_POLICY,
                Instant.parse("2026-07-20T02:00:00Z")
            )
        );
        return snapshotRepository.findBy(key).orElseThrow().id();
    }

    private long countRows(String tableName) {
        return jdbcTemplate.queryForObject(
            "select count(*) from " + tableName,
            Long.class
        );
    }

    private String otherTableName(RankingPeriod period) {
        return switch (period) {
            case WEEKLY -> "mv_product_rank_monthly";
            case MONTHLY -> "mv_product_rank_weekly";
            case DAILY -> throw new IllegalArgumentException("DAILY is not supported");
        };
    }
}
