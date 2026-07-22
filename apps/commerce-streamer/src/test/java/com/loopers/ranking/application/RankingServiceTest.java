package com.loopers.ranking.application;

import com.loopers.ranking.domain.RankingEntry;
import com.loopers.ranking.domain.RankingRepository;
import com.loopers.ranking.domain.RankingScoreDelta;
import com.loopers.ranking.domain.RankingSignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankingServiceTest {

    /** incrBy 인자를 그대로 담아두는 fake. */
    private static class CapturingRepository implements RankingRepository {
        final List<RankingScoreDelta> captured = new ArrayList<>();

        @Override
        public void incrBy(List<RankingScoreDelta> deltas) {
            captured.addAll(deltas);
        }

        @Override
        public void carryOver(LocalDate from, LocalDate to, double weight) {
        }

        @Override
        public List<RankingEntry> readDesc(LocalDate date) {
            return List.of();
        }

        @Override
        public void rebuild(LocalDate date, List<RankingEntry> seeds) {
        }
    }

    @DisplayName("같은 날짜·상품의 여러 이벤트는 점수를 합산해 한 delta 로 반영한다")
    @Test
    void foldsSameBucket_intoSingleDelta() {
        // Arrange
        CapturingRepository repo = new CapturingRepository();
        RankingService service = new RankingService(repo);
        LocalDate date = LocalDate.of(2026, 7, 17);

        // Act — 상품 101: 조회(0.1) + 주문 2개(1.2) = 1.3
        service.apply(List.of(
                new RankingEvent(date, 101L, RankingSignal.VIEW, 1),
                new RankingEvent(date, 101L, RankingSignal.ORDER, 2)
        ));

        // Assert
        assertThat(repo.captured).hasSize(1);
        RankingScoreDelta delta = repo.captured.get(0);
        assertThat(delta.date()).isEqualTo(date);
        assertThat(delta.productId()).isEqualTo(101L);
        assertThat(delta.score()).isCloseTo(1.3, within(1e-9));
    }

    @DisplayName("날짜나 상품이 다르면 별도 delta 로 분리한다")
    @Test
    void separatesDifferentBuckets() {
        // Arrange
        CapturingRepository repo = new CapturingRepository();
        RankingService service = new RankingService(repo);
        LocalDate today = LocalDate.of(2026, 7, 17);
        LocalDate yesterday = LocalDate.of(2026, 7, 16);

        // Act
        service.apply(List.of(
                new RankingEvent(today, 101L, RankingSignal.LIKE, 1),
                new RankingEvent(today, 202L, RankingSignal.LIKE, 1),
                new RankingEvent(yesterday, 101L, RankingSignal.LIKE, 1)
        ));

        // Assert
        assertThat(repo.captured).hasSize(3);
    }
}
