package com.loopers.application.ranking;

import com.loopers.domain.ranking.ProductRankBatchRun;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProductRankSnapshotService {

    private final ProductRankBatchRunRepository batchRunRepository;
    private final ProductRankSnapshotRepository snapshotRepository;

    public ProductRankSnapshotService(
        ProductRankBatchRunRepository batchRunRepository,
        ProductRankSnapshotRepository snapshotRepository
    ) {
        this.batchRunRepository = batchRunRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @Transactional
    public void activateSnapshot(Long batchRunId) {
        ProductRankBatchRun batchRun = batchRunRepository.findById(batchRunId)
            .orElseThrow(() -> new IllegalArgumentException("batchRun not found."));

        if (snapshotRepository.existsInvalidSnapshot(batchRunId)) {
            batchRunRepository.markFailed(batchRunId);
            throw new IllegalStateException("Invalid ranking snapshot.");
        }

        snapshotRepository.deactivateActiveSnapshot(
            batchRun.getPeriod(),
            batchRun.getRankStartDate(),
            batchRun.getRankEndDate()
        );
        snapshotRepository.activateSnapshot(batchRunId);
        batchRunRepository.markCompleted(batchRunId);
    }
}
