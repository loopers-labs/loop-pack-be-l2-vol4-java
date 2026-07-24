package com.loopers.infrastructure.ranking.batch;

import com.loopers.domain.ranking.batch.RankingBatchPeriodType;
import com.loopers.domain.ranking.batch.RankingStagingRankRow;
import com.loopers.domain.ranking.batch.RankingStagingRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class RankingStagingRepositoryImplIntegrationTest {

    @Autowired private RankingStagingRepository repository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("saveAll()로 저장하고 findByPeriod()로 조회할 때,")
    @Nested
    class SaveAndFind {

        @DisplayName("저장한 행을 rank 오름차순으로 그대로 돌려준다.")
        @Test
        void returnsSavedRowsOrderedByRank() {
            List<RankingStagingRankRow> rows = List.of(
                new RankingStagingRankRow(1, 100L, 30.0),
                new RankingStagingRankRow(2, 200L, 20.0)
            );

            repository.saveAll(RankingBatchPeriodType.WEEKLY, "2026W29", rows);
            List<RankingStagingRankRow> found = repository.findByPeriod(RankingBatchPeriodType.WEEKLY, "2026W29");

            assertThat(found).extracting(RankingStagingRankRow::rank).containsExactly(1, 2);
            assertThat(found).extracting(RankingStagingRankRow::productId).containsExactly(100L, 200L);
            assertThat(found).extracting(RankingStagingRankRow::score).containsExactly(30.0, 20.0);
        }

        @DisplayName("WEEKLY와 MONTHLY, 다른 periodKey는 서로 섞이지 않는다.")
        @Test
        void keepsPeriodsIsolated() {
            repository.saveAll(RankingBatchPeriodType.WEEKLY, "2026W29", List.of(new RankingStagingRankRow(1, 1L, 1.0)));
            repository.saveAll(RankingBatchPeriodType.WEEKLY, "2026W30", List.of(new RankingStagingRankRow(1, 2L, 2.0)));
            repository.saveAll(RankingBatchPeriodType.MONTHLY, "2026W29", List.of(new RankingStagingRankRow(1, 3L, 3.0)));

            List<RankingStagingRankRow> found = repository.findByPeriod(RankingBatchPeriodType.WEEKLY, "2026W29");

            assertThat(found).hasSize(1);
            assertThat(found.get(0).productId()).isEqualTo(1L);
        }
    }

    @DisplayName("deleteByPeriod()를 실행할 때,")
    @Nested
    class DeleteByPeriod {

        @DisplayName("해당 period의 행만 삭제하고 다른 period는 남긴다.")
        @Test
        void deletesOnlyMatchingPeriod() {
            repository.saveAll(RankingBatchPeriodType.WEEKLY, "2026W29", List.of(new RankingStagingRankRow(1, 1L, 1.0)));
            repository.saveAll(RankingBatchPeriodType.WEEKLY, "2026W30", List.of(new RankingStagingRankRow(1, 2L, 2.0)));

            repository.deleteByPeriod(RankingBatchPeriodType.WEEKLY, "2026W29");

            assertThat(repository.findByPeriod(RankingBatchPeriodType.WEEKLY, "2026W29")).isEmpty();
            assertThat(repository.findByPeriod(RankingBatchPeriodType.WEEKLY, "2026W30")).hasSize(1);
        }
    }
}
