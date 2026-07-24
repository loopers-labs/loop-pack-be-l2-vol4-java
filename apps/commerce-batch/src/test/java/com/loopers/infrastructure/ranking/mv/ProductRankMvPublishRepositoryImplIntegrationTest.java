package com.loopers.infrastructure.ranking.mv;

import com.loopers.domain.ranking.mv.ProductRankMvPublishRepository;
import com.loopers.domain.ranking.mv.ProductRankMvRow;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class ProductRankMvPublishRepositoryImplIntegrationTest {

    @Autowired private ProductRankMvPublishRepository repository;
    @Autowired private MvProductRankWeeklyJpaRepository weeklyJpaRepository;
    @Autowired private MvProductRankMonthlyJpaRepository monthlyJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private final ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("replaceWeeklyPeriod()를 실행할 때,")
    @Nested
    class ReplaceWeeklyPeriod {

        @DisplayName("해당 periodKey에 새 순위 행들을 게시한다.")
        @Test
        void publishesRowsForPeriod() {
            List<ProductRankMvRow> rows = List.of(
                new ProductRankMvRow(100L, 1, 30.0),
                new ProductRankMvRow(200L, 2, 20.0)
            );

            repository.replaceWeeklyPeriod("2026W29", rows, now);

            List<MvProductRankWeeklyEntity> found = weeklyJpaRepository.findByPeriodKeyOrderByRankPositionAsc("2026W29");
            assertThat(found).extracting(MvProductRankWeeklyEntity::getProductId).containsExactly(100L, 200L);
            assertThat(found).extracting(MvProductRankWeeklyEntity::getRankPosition).containsExactly(1, 2);
        }

        @DisplayName("같은 periodKey에 다시 게시하면 기존 행을 지우고 새 행으로 교체한다.")
        @Test
        void replacesExistingRowsForSamePeriod() {
            repository.replaceWeeklyPeriod("2026W29", List.of(new ProductRankMvRow(100L, 1, 30.0)), now);

            repository.replaceWeeklyPeriod("2026W29", List.of(new ProductRankMvRow(999L, 1, 99.0)), now);

            List<MvProductRankWeeklyEntity> found = weeklyJpaRepository.findByPeriodKeyOrderByRankPositionAsc("2026W29");
            assertThat(found).hasSize(1);
            assertThat(found.get(0).getProductId()).isEqualTo(999L);
        }

        @DisplayName("다른 periodKey의 기존 행은 건드리지 않는다.")
        @Test
        void doesNotAffectOtherPeriods() {
            repository.replaceWeeklyPeriod("2026W29", List.of(new ProductRankMvRow(100L, 1, 30.0)), now);

            repository.replaceWeeklyPeriod("2026W30", List.of(new ProductRankMvRow(200L, 1, 20.0)), now);

            assertThat(weeklyJpaRepository.findByPeriodKeyOrderByRankPositionAsc("2026W29")).hasSize(1);
            assertThat(weeklyJpaRepository.findByPeriodKeyOrderByRankPositionAsc("2026W30")).hasSize(1);
        }
    }

    @DisplayName("replaceMonthlyPeriod()를 실행할 때,")
    @Nested
    class ReplaceMonthlyPeriod {

        @DisplayName("해당 periodKey에 새 순위 행들을 게시한다.")
        @Test
        void publishesRowsForPeriod() {
            List<ProductRankMvRow> rows = List.of(new ProductRankMvRow(100L, 1, 30.0));

            repository.replaceMonthlyPeriod("202607", rows, now);

            List<MvProductRankMonthlyEntity> found = monthlyJpaRepository.findByPeriodKeyOrderByRankPositionAsc("202607");
            assertThat(found).extracting(MvProductRankMonthlyEntity::getProductId).containsExactly(100L);
        }
    }
}
