package com.loopers.application.ranking;

import com.loopers.application.metrics.CatalogEventMessage;
import com.loopers.application.metrics.OrderEventMessage;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingScorePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankingProcessorTest {

    private final FakeRankingRepository fake = new FakeRankingRepository();
    private final RankingProcessor processor =
        new RankingProcessor(new RankingScorePolicy(0.1, 0.2, 0.6), fake);

    @DisplayName("조회 이벤트는 occurredAt 의 KST 날짜 키로 +0.1 delta 를 반영한다.")
    @Test
    void catalogViewApplied() {
        processor.handleCatalog(new CatalogEventMessage(
            "e1", "ProductViewed", 101L, 0, 0, "2026-07-15T03:00:00Z"));

        FakeRankingRepository.Applied applied = fake.applied.get(0);
        assertThat(applied.eventId()).isEqualTo("e1");
        assertThat(applied.date()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(applied.deltas().get(101L)).isCloseTo(0.1, within(1e-9));
    }

    @DisplayName("좋아요 취소는 -0.2 delta 를 반영한다.")
    @Test
    void likeRemovedAppliesNegativeDelta() {
        processor.handleCatalog(new CatalogEventMessage(
            "e2", "LikeRemoved", 101L, 3, 7, "2026-07-15T03:00:00Z"));

        assertThat(fake.applied.get(0).deltas().get(101L)).isCloseTo(-0.2, within(1e-9));
    }

    @DisplayName("미지 카탈로그 타입은 랭킹에 반영하지 않는다.")
    @Test
    void unknownCatalogTypeSkipped() {
        processor.handleCatalog(new CatalogEventMessage(
            "e3", "SomethingElse", 101L, 0, 0, "2026-07-15T03:00:00Z"));

        assertThat(fake.applied).isEmpty();
    }

    @DisplayName("주문은 라인별 0.6×수량 — 같은 상품 라인은 합산되어 한 번의 applyOnce 로 반영된다.")
    @Test
    void orderLinesMergedPerProduct() {
        processor.handleOrder(new OrderEventMessage("o1", "OrderPlaced", 1L,
            List.of(
                new OrderEventMessage.Line(101L, 2),
                new OrderEventMessage.Line(102L, 1),
                new OrderEventMessage.Line(101L, 1)
            ), "2026-07-15T03:00:00Z"));

        assertThat(fake.applied).hasSize(1);
        Map<Long, Double> deltas = fake.applied.get(0).deltas();
        assertThat(deltas.get(101L)).isCloseTo(1.8, within(1e-9)); // 0.6×(2+1)
        assertThat(deltas.get(102L)).isCloseTo(0.6, within(1e-9));
    }

    static class FakeRankingRepository implements RankingRepository {
        record Applied(String eventId, LocalDate date, Map<Long, Double> deltas) {}
        final List<Applied> applied = new ArrayList<>();

        @Override
        public boolean applyOnce(String eventId, LocalDate date, Map<Long, Double> deltas) {
            applied.add(new Applied(eventId, date, deltas));
            return true;
        }

        @Override
        public Optional<Double> findScore(LocalDate date, Long productId) { return Optional.empty(); }

        @Override
        public void carryOver(LocalDate from, LocalDate to, double rate) {}
    }
}
