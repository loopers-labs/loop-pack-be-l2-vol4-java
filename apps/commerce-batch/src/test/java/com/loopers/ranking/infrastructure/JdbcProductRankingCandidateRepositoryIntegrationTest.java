package com.loopers.ranking.infrastructure;

import com.loopers.ranking.RankingPeriod;
import com.loopers.ranking.RankingScorePolicy;
import com.loopers.ranking.application.NewProductRankingSnapshot;
import com.loopers.ranking.application.ProductRankingSnapshotKey;
import com.loopers.ranking.application.RankingCandidate;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = "spring.batch.job.enabled=false")
class JdbcProductRankingCandidateRepositoryIntegrationTest {

    private static final RankingScorePolicy SCORE_POLICY =
        new RankingScorePolicy(0.1, 0.2, 0.7, 10_000);

    private final JdbcProductRankingCandidateRepository candidateRepository;
    private final JdbcProductRankingSnapshotRepository snapshotRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    JdbcProductRankingCandidateRepositoryIntegrationTest(
        JdbcProductRankingCandidateRepository candidateRepository,
        JdbcProductRankingSnapshotRepository snapshotRepository,
        JdbcTemplate jdbcTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.candidateRepository = candidateRepository;
        this.snapshotRepository = snapshotRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("기간과 무관한 후보 테이블에 절대 점수를 UPSERT한다.")
    @EnumSource(value = RankingPeriod.class, names = {"WEEKLY", "MONTHLY"})
    @ParameterizedTest
    void upsertsAbsoluteScore(RankingPeriod period) {
        // arrange
        long snapshotId = insertSnapshot(period);
        candidateRepository.upsertAll(
            List.of(new RankingCandidate(snapshotId, 101L, 5.3))
        );

        // act
        candidateRepository.upsertAll(
            List.of(new RankingCandidate(snapshotId, 101L, 7.7))
        );

        // assert
        CandidateRow result = jdbcTemplate.queryForObject(
            """
                select product_id, score
                from product_rank_candidates
                where snapshot_id = ?
                """,
            (resultSet, rowNumber) -> new CandidateRow(
                resultSet.getLong("product_id"),
                resultSet.getDouble("score")
            ),
            snapshotId
        );
        assertAll(
            () -> assertThat(result)
                .isEqualTo(new CandidateRow(101L, 7.7)),
            () -> assertThat(countRows("mv_product_rank_weekly")).isZero(),
            () -> assertThat(countRows("mv_product_rank_monthly")).isZero()
        );
    }

    @DisplayName("점수 내림차순과 상품 ID 오름차순으로 상위 후보를 조회한다.")
    @EnumSource(value = RankingPeriod.class, names = {"WEEKLY", "MONTHLY"})
    @ParameterizedTest
    void findsTopCandidatesInDeterministicOrder(RankingPeriod period) {
        // arrange
        long snapshotId = insertSnapshot(period);
        candidateRepository.upsertAll(List.of(
            new RankingCandidate(snapshotId, 402L, 5.3),
            new RankingCandidate(snapshotId, 303L, 10.0),
            new RankingCandidate(snapshotId, 401L, 5.3)
        ));

        // act
        List<RankingCandidate> result =
            candidateRepository.findTopCandidates(snapshotId, 2);

        // assert
        assertAll(
            () -> assertThat(candidateRepository.countCandidates(snapshotId))
                .isEqualTo(3),
            () -> assertThat(result).containsExactly(
                new RankingCandidate(snapshotId, 303L, 10.0),
                new RankingCandidate(snapshotId, 401L, 5.3)
            )
        );
    }

    @DisplayName("지정한 Snapshot 후보를 상품 ID 순서로 제한된 수만큼 삭제한다.")
    @EnumSource(value = RankingPeriod.class, names = {"WEEKLY", "MONTHLY"})
    @ParameterizedTest
    void deletesCandidatesInBatches(RankingPeriod period) {
        // arrange
        long snapshotId = insertSnapshot(period);
        candidateRepository.upsertAll(List.of(
            new RankingCandidate(snapshotId, 101L, 10.0),
            new RankingCandidate(snapshotId, 202L, 7.0),
            new RankingCandidate(snapshotId, 303L, 5.0)
        ));

        // act
        int firstDeleted = candidateRepository.deleteCandidates(snapshotId, 2);
        List<Long> productIdsAfterFirstDelete = jdbcTemplate.queryForList(
            """
                select product_id
                from product_rank_candidates
                where snapshot_id = ?
                order by product_id
                """,
            Long.class,
            snapshotId
        );
        int secondDeleted = candidateRepository.deleteCandidates(snapshotId, 2);
        int thirdDeleted = candidateRepository.deleteCandidates(snapshotId, 2);

        // assert
        assertAll(
            () -> assertThat(firstDeleted).isEqualTo(2),
            () -> assertThat(productIdsAfterFirstDelete).containsExactly(303L),
            () -> assertThat(secondDeleted).isEqualTo(1),
            () -> assertThat(thirdDeleted).isZero(),
            () -> assertThat(candidateRepository.countCandidates(snapshotId)).isZero()
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

    private record CandidateRow(long productId, double score) {
    }
}
