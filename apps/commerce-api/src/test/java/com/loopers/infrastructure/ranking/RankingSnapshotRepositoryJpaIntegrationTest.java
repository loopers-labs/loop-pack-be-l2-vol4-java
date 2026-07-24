package com.loopers.infrastructure.ranking;

import com.loopers.application.ranking.RankingEntry;
import com.loopers.application.ranking.RankingSnapshotRepository;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.testcontainers.RedisTestContainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = RedisTestContainersConfig.class)
class RankingSnapshotRepositoryJpaIntegrationTest {

    @Autowired
    private RankingSnapshotRepository rankingSnapshotRepository;

    @Autowired
    private ProductRankWeeklyMvJpaRepository weeklyRepository;

    @Autowired
    private ProductRankMonthlyMvJpaRepository monthlyRepository;

    @BeforeEach
    void setUp() {
        weeklyRepository.deleteAll();
        monthlyRepository.deleteAll();
    }

    @Test
    @DisplayName("주간 MV 조회는 active Snapshot만 rank_no 오름차순으로 반환한다.")
    void findRankings_WhenWeekly_ShouldReturnActiveSnapshotsOrderByRankNo() {
        weeklyRepository.save(new ProductRankWeeklyMvJpaEntity(
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26),
            1L,
            2,
            20.0,
            true
        ));
        weeklyRepository.save(new ProductRankWeeklyMvJpaEntity(
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26),
            2L,
            1,
            30.0,
            true
        ));
        weeklyRepository.save(new ProductRankWeeklyMvJpaEntity(
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26),
            3L,
            1,
            100.0,
            false
        ));

        var result = rankingSnapshotRepository.findRankings(
            RankingPeriod.WEEKLY,
            "20260720",
            "20260726",
            1,
            20
        );

        assertThat(result).containsExactly(
            new RankingEntry(2L, 1, 30.0),
            new RankingEntry(1L, 2, 20.0)
        );
        assertThat(rankingSnapshotRepository.count(RankingPeriod.WEEKLY, "20260720", "20260726")).isEqualTo(2);
    }

    @Test
    @DisplayName("월간 MV 조회는 active Snapshot만 rank_no 오름차순으로 반환한다.")
    void findRankings_WhenMonthly_ShouldReturnActiveSnapshotsOrderByRankNo() {
        monthlyRepository.save(new ProductRankMonthlyMvJpaEntity(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            10L,
            1,
            50.0,
            true
        ));
        monthlyRepository.save(new ProductRankMonthlyMvJpaEntity(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            11L,
            2,
            40.0,
            false
        ));

        var result = rankingSnapshotRepository.findRankings(
            RankingPeriod.MONTHLY,
            "20260701",
            "20260731",
            1,
            20
        );

        assertThat(result).containsExactly(new RankingEntry(10L, 1, 50.0));
        assertThat(rankingSnapshotRepository.count(RankingPeriod.MONTHLY, "20260701", "20260731")).isEqualTo(1);
    }
}
