package com.loopers.application.ranking;

import com.loopers.application.metrics.CatalogEventMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogRankingEventProcessorTest {
    @DisplayName("같은 한국 날짜와 상품의 이벤트는 합산해 Redis를 한 번만 갱신한다.")
    @Test
    void aggregatesScoresByKoreanDateAndProduct() {
        // arrange
        RankingScoreWriter writer = mock(RankingScoreWriter.class);
        CatalogRankingEventProcessor processor = new CatalogRankingEventProcessor(
            new RankingScorePolicy(), writer, new SimpleMeterRegistry()
        );
        CatalogEventMessage view = event("PRODUCT_VIEWED", "2026-07-16T15:30:00Z", Map.of("viewCountDelta", 1));
        CatalogEventMessage order = event("PRODUCT_ORDERED", "2026-07-17T14:59:00Z", Map.of("salesCountDelta", 1));

        // act
        processor.process(List.of(view, order));

        // assert
        verify(writer).increment("ranking:all:20260717", 1L, 0.1 + 0.7);
        verifyNoMoreInteractions(writer);
    }

    @DisplayName("같은 상품이어도 한국 날짜가 다르면 각 일간 키를 갱신한다.")
    @Test
    void separatesScoresByKoreanDate() {
        RankingScoreWriter writer = mock(RankingScoreWriter.class);
        CatalogRankingEventProcessor processor = new CatalogRankingEventProcessor(
            new RankingScorePolicy(), writer, new SimpleMeterRegistry()
        );

        processor.process(List.of(
            event("PRODUCT_VIEWED", "2026-07-17T14:59:00Z", Map.of("viewCountDelta", 1)),
            event("PRODUCT_VIEWED", "2026-07-17T15:00:00Z", Map.of("viewCountDelta", 1))
        ));

        verify(writer).increment("ranking:all:20260717", 1L, 0.1);
        verify(writer).increment("ranking:all:20260718", 1L, 0.1);
    }

    @DisplayName("배치에 semantic invalid 이벤트가 하나라도 있으면 Redis를 쓰기 전에 전체 배치를 실패시킨다.")
    @Test
    void validatesWholeBatchBeforeAnyWrite() {
        RankingScoreWriter writer = mock(RankingScoreWriter.class);
        CatalogRankingEventProcessor processor = new CatalogRankingEventProcessor(
            new RankingScorePolicy(), writer, new SimpleMeterRegistry()
        );
        CatalogEventMessage valid = event(
            "PRODUCT_VIEWED", "2026-07-17T09:00:00+09:00", Map.of("viewCountDelta", 1)
        );
        CatalogEventMessage invalid = event(
            "PRODUCT_ORDERED", "2026-07-17T09:00:00+09:00", Map.of("salesCountDelta", "invalid")
        );

        assertThrows(IllegalArgumentException.class, () -> processor.process(List.of(valid, invalid)));

        verifyNoInteractions(writer);
    }

    @DisplayName("이벤트 타입과 점수 방향별 누적량을 기록한다.")
    @Test
    void recordsScoreAccumulationByEventTypeAndDirection() {
        RankingScoreWriter writer = mock(RankingScoreWriter.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CatalogRankingEventProcessor processor = new CatalogRankingEventProcessor(
            new RankingScorePolicy(), writer, meterRegistry
        );

        processor.process(List.of(
            event("PRODUCT_LIKED", "2026-07-17T09:00:00+09:00", Map.of("likeCountDelta", 1)),
            event("PRODUCT_UNLIKED", "2026-07-17T09:00:00+09:00", Map.of("likeCountDelta", -1))
        ));

        assertThat(meterRegistry.counter(
            "ranking_score_total", "eventType", "PRODUCT_LIKED", "direction", "positive"
        ).count()).isEqualTo(0.2);
        assertThat(meterRegistry.counter(
            "ranking_score_total", "eventType", "PRODUCT_UNLIKED", "direction", "negative"
        ).count()).isEqualTo(0.2);
    }

    private CatalogEventMessage event(String type, String occurredAt, Map<String, Object> data) {
        return new CatalogEventMessage("event", type, "PRODUCT", 1L, ZonedDateTime.parse(occurredAt), data);
    }
}
