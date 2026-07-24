package com.loopers.ranking.application;

import com.loopers.ranking.RankingScorePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;

@RequiredArgsConstructor
@Service
public class ProductRankingPreparationService {

    private final ProductRankingSnapshotRepository repository;
    private final RankingScorePolicy currentScorePolicy;
    private final Clock clock;

    public void prepare(ProductRankingSnapshotKey key) {
        repository.findBy(key).ifPresentOrElse(
            this::reuseIncompleteSnapshot,
            () -> repository.insert(
                new NewProductRankingSnapshot(key, currentScorePolicy, clock.instant())
            )
        );
    }

    private void reuseIncompleteSnapshot(ProductRankingSnapshotHeader snapshot) {
        if (snapshot.isCompleted()) {
            throw new IllegalStateException(
                "completed product ranking snapshot cannot be prepared again"
            );
        }
    }
}
