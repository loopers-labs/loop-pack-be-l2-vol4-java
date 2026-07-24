package com.loopers.application.ranking;

import com.loopers.domain.ranking.BatchRunStatus;
import com.loopers.domain.ranking.ProductRankBatchRun;
import com.loopers.domain.ranking.ProductRankSnapshot;
import com.loopers.domain.ranking.RankingPeriod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductRankSnapshotServiceTest {

    @Test
    @DisplayName("검증 성공 시 기존 active Snapshot을 비활성화하고 새 Snapshot을 활성화한다.")
    void activateSnapshot_WhenValid_ShouldSwitchActiveSnapshot() {
        FakeProductRankBatchRunRepository batchRunRepository = new FakeProductRankBatchRunRepository();
        FakeProductRankSnapshotRepository snapshotRepository = new FakeProductRankSnapshotRepository();
        ProductRankSnapshotService service = new ProductRankSnapshotService(batchRunRepository, snapshotRepository);

        ProductRankBatchRun oldRun = batchRunRepository.create(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26)
        );
        ProductRankBatchRun newRun = batchRunRepository.create(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26)
        );
        ProductRankSnapshot oldSnapshot = ProductRankSnapshot.createInactive(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26),
            oldRun.getId(),
            10L,
            1,
            98.1
        );
        oldSnapshot.activate();
        snapshotRepository.saveAll(List.of(oldSnapshot));
        ProductRankSnapshot newSnapshot = ProductRankSnapshot.createInactive(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26),
            newRun.getId(),
            20L,
            1,
            101.1
        );
        snapshotRepository.saveAll(List.of(newSnapshot));

        service.activateSnapshot(newRun.getId());

        assertThat(oldSnapshot.isActive()).isFalse();
        assertThat(newSnapshot.isActive()).isTrue();
        assertThat(newRun.getStatus()).isEqualTo(BatchRunStatus.COMPLETED);
    }

    @Test
    @DisplayName("검증 실패 시 기존 active Snapshot을 유지하고 실행 상태를 실패로 기록한다.")
    void activateSnapshot_WhenInvalid_ShouldKeepExistingActiveSnapshotAndMarkFailed() {
        FakeProductRankBatchRunRepository batchRunRepository = new FakeProductRankBatchRunRepository();
        FakeProductRankSnapshotRepository snapshotRepository = new FakeProductRankSnapshotRepository();
        ProductRankSnapshotService service = new ProductRankSnapshotService(batchRunRepository, snapshotRepository);

        ProductRankBatchRun oldRun = batchRunRepository.create(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26)
        );
        ProductRankBatchRun newRun = batchRunRepository.create(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26)
        );
        ProductRankSnapshot oldSnapshot = ProductRankSnapshot.createInactive(
            RankingPeriod.WEEKLY,
            LocalDate.of(2026, 7, 20),
            LocalDate.of(2026, 7, 26),
            oldRun.getId(),
            10L,
            1,
            98.1
        );
        oldSnapshot.activate();
        snapshotRepository.saveAll(List.of(oldSnapshot));
        snapshotRepository.invalidBatchRunIds.add(newRun.getId());

        assertThatThrownBy(() -> service.activateSnapshot(newRun.getId()))
            .isInstanceOf(IllegalStateException.class);

        assertThat(oldSnapshot.isActive()).isTrue();
        assertThat(newRun.getStatus()).isEqualTo(BatchRunStatus.FAILED);
    }

    private static class FakeProductRankBatchRunRepository implements ProductRankBatchRunRepository {
        private final Map<Long, ProductRankBatchRun> batchRuns = new HashMap<>();
        private long sequence = 1L;

        @Override
        public ProductRankBatchRun create(RankingPeriod period, LocalDate rankStartDate, LocalDate rankEndDate) {
            ProductRankBatchRun batchRun = ProductRankBatchRun.restore(sequence++, period, rankStartDate, rankEndDate);
            batchRuns.put(batchRun.getId(), batchRun);
            return batchRun;
        }

        @Override
        public Optional<ProductRankBatchRun> findById(Long batchRunId) {
            return Optional.ofNullable(batchRuns.get(batchRunId));
        }

        @Override
        public void markCompleted(Long batchRunId) {
            batchRuns.get(batchRunId).markCompleted();
        }

        @Override
        public void markFailed(Long batchRunId) {
            batchRuns.get(batchRunId).markFailed();
        }
    }

    private static class FakeProductRankSnapshotRepository implements ProductRankSnapshotRepository {
        private final List<ProductRankSnapshot> snapshots = new ArrayList<>();
        private final List<Long> invalidBatchRunIds = new ArrayList<>();

        @Override
        public void saveAll(List<ProductRankSnapshot> snapshots) {
            this.snapshots.addAll(snapshots);
        }

        @Override
        public boolean existsInvalidSnapshot(Long batchRunId) {
            return invalidBatchRunIds.contains(batchRunId);
        }

        @Override
        public void deactivateActiveSnapshot(RankingPeriod period, LocalDate rankStartDate, LocalDate rankEndDate) {
            snapshots.stream()
                .filter(ProductRankSnapshot::isActive)
                .filter(snapshot -> snapshot.getPeriod() == period)
                .filter(snapshot -> snapshot.getRankStartDate().equals(rankStartDate))
                .filter(snapshot -> snapshot.getRankEndDate().equals(rankEndDate))
                .forEach(ProductRankSnapshot::deactivate);
        }

        @Override
        public void activateSnapshot(Long batchRunId) {
            snapshots.stream()
                .filter(snapshot -> snapshot.getBatchRunId().equals(batchRunId))
                .forEach(ProductRankSnapshot::activate);
        }
    }
}
