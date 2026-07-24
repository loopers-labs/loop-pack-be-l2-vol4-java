package com.loopers.domain.ranking.batch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * min-heap(용량 100)으로 Top100을 스트리밍 누적한다. 상품이 몇만 개든 메모리에는
 * 항상 100개만 유지되며, 힙이 가득 찬 뒤로는 최저 점수 후보만 교체된다.
 */
public class RankingTop100Accumulator {

    public static final int CAPACITY = 100;

    private final PriorityQueue<RankingScoreCandidate> heap =
        new PriorityQueue<>(CAPACITY + 1, Comparator.comparingDouble(RankingScoreCandidate::score));

    public void add(RankingScoreCandidate candidate) {
        if (heap.size() < CAPACITY) {
            heap.offer(candidate);
            return;
        }
        RankingScoreCandidate lowest = heap.peek();
        if (lowest != null && candidate.score() > lowest.score()) {
            heap.poll();
            heap.offer(candidate);
        }
    }

    public List<RankingStagingRankRow> drainRanked() {
        List<RankingScoreCandidate> sorted = new ArrayList<>(heap);
        sorted.sort(
            Comparator.comparingDouble(RankingScoreCandidate::score).reversed()
                .thenComparing(RankingScoreCandidate::productId)
        );

        List<RankingStagingRankRow> rows = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            RankingScoreCandidate candidate = sorted.get(i);
            rows.add(new RankingStagingRankRow(i + 1, candidate.productId(), candidate.score()));
        }
        return rows;
    }
}
