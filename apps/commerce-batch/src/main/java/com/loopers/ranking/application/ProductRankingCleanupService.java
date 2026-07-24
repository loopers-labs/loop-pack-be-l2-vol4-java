package com.loopers.ranking.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ProductRankingCleanupService {

    private final ProductRankingSnapshotRepository snapshotRepository;
    private final ProductRankingCandidateRepository candidateRepository;

    public ProductRankingSnapshotHeader validateCleanupTarget(
        long targetSnapshotId
    ) {
        ProductRankingSnapshotHeader target = snapshotRepository
            .findById(targetSnapshotId)
            .orElseThrow(() -> new IllegalStateException(
                "product ranking cleanup target does not exist"
            ));
        if (!target.isCompleted()) {
            throw new IllegalStateException(
                "product ranking cleanup target must be completed"
            );
        }
        if (!snapshotRepository.existsNewerCompletedThan(target.key())) {
            throw new IllegalStateException(
                "newer completed product ranking snapshot does not exist"
            );
        }
        return target;
    }

    public int deleteCandidates(
        ProductRankingSnapshotHeader target,
        int limit
    ) {
        return candidateRepository.deleteCandidates(target.id(), limit);
    }
}
