package com.loopers.ranking.application;

import com.loopers.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Service
public class ProductRankingPublicationService {

    static final int TOP_LIMIT = 100;
    static final int VERIFICATION_LIMIT = TOP_LIMIT + 1;
    private static final Comparator<ProductRankingAssignment> RANKING_ORDER =
        Comparator.comparingDouble(ProductRankingAssignment::score)
            .reversed()
            .thenComparingLong(ProductRankingAssignment::productId);

    private final ProductRankingSnapshotRepository snapshotRepository;
    private final ProductRankingCandidateRepository candidateRepository;
    private final ProductRankingResultRepository resultRepository;
    private final ProductRankingSourceQuery sourceQuery;
    private final Clock clock;

    public void publish(ProductRankingSnapshotKey key) {
        Objects.requireNonNull(key, "key must not be null");
        ProductRankingSnapshotHeader snapshot = snapshotRepository.findBy(key)
            .orElseThrow(() -> new IllegalStateException(
                "product ranking snapshot does not exist"
            ));

        if (snapshot.isCompleted()) {
            validatePublishedRankings(findPublishedRankings(snapshot));
            return;
        }

        long candidateCount = validateCandidateCount(snapshot);
        validateNoExistingRanks(snapshot);

        List<ProductRankingAssignment> expectedRankings = assignSequentialRanks(
            snapshot,
            candidateCount,
            candidateRepository.findTopCandidates(
                snapshot.id(),
                TOP_LIMIT
            )
        );
        resultRepository.insertAll(
            snapshot.key().period(),
            snapshot.id(),
            expectedRankings
        );

        List<ProductRankingAssignment> publishedRankings = findPublishedRankings(snapshot);
        validatePublishedRankings(publishedRankings);
        if (!publishedRankings.equals(expectedRankings)) {
            throw new IllegalStateException(
                "published product rankings do not match expected rankings"
            );
        }

        if (!snapshotRepository.completeIfIncomplete(snapshot.id(), clock.instant())) {
            throw new IllegalStateException(
                "product ranking snapshot completion conflict"
            );
        }
    }

    private long validateCandidateCount(ProductRankingSnapshotHeader snapshot) {
        ProductRankingSnapshotKey key = snapshot.key();
        long sourceCount = sourceQuery.countProductsWithMetrics(
            key.periodStart(),
            key.aggregationEndDate()
        );
        long candidateCount = candidateRepository.countCandidates(snapshot.id());
        if (sourceCount != candidateCount) {
            throw new IllegalStateException(
                "source product count and candidate count do not match: source=%d, candidate=%d"
                    .formatted(sourceCount, candidateCount)
            );
        }
        return candidateCount;
    }

    private void validateNoExistingRanks(ProductRankingSnapshotHeader snapshot) {
        List<ProductRankingAssignment> existingRanks =
            resultRepository.findPublishedRankings(
                snapshot.key().period(),
                snapshot.id(),
                1
            );
        if (!existingRanks.isEmpty()) {
            throw new IllegalStateException(
                "incomplete product ranking snapshot already has rank assignments"
            );
        }
    }

    private List<ProductRankingAssignment> assignSequentialRanks(
        ProductRankingSnapshotHeader snapshot,
        long candidateCount,
        List<RankingCandidate> candidates
    ) {
        int expectedCount = Math.toIntExact(Math.min(
            candidateCount,
            TOP_LIMIT
        ));
        if (candidates.size() != expectedCount) {
            throw new IllegalStateException(
                "top candidate count does not match expected published count"
            );
        }

        List<ProductRankingAssignment> assignments = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            RankingCandidate candidate = candidates.get(index);
            if (candidate.snapshotId() != snapshot.id()) {
                throw new IllegalStateException(
                    "top candidate belongs to a different snapshot"
                );
            }
            assignments.add(new ProductRankingAssignment(
                candidate.productId(),
                candidate.score(),
                index + 1
            ));
        }
        validatePublishedRankings(assignments);
        return List.copyOf(assignments);
    }

    private List<ProductRankingAssignment> findPublishedRankings(
        ProductRankingSnapshotHeader snapshot
    ) {
        return resultRepository.findPublishedRankings(
            snapshot.key().period(),
            snapshot.id(),
            VERIFICATION_LIMIT
        );
    }

    private void validatePublishedRankings(
        List<ProductRankingAssignment> rankings
    ) {
        if (rankings.size() > TOP_LIMIT) {
            throw new IllegalStateException(
                "published product ranking count exceeds " + TOP_LIMIT
            );
        }

        ProductRankingAssignment previous = null;
        for (int index = 0; index < rankings.size(); index++) {
            ProductRankingAssignment current = rankings.get(index);
            if (current.rankNo() != index + 1) {
                throw new IllegalStateException(
                    "published product ranking rank numbers are not continuous"
                );
            }
            if (previous != null && RANKING_ORDER.compare(previous, current) > 0) {
                throw new IllegalStateException(
                    "published product ranking order is invalid"
                );
            }
            previous = current;
        }
    }
}
