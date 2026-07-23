package com.loopers.application.ranking;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loopers.application.metrics.EventEnvelope;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 랭킹 집계 로직 단위 테스트 — Kafka/Redis/DB 없이 검증한다.
 * eventType 별 스코어 해석 + 배치 내 (일간 키, productId) coalescing + 일자 버킷 분기 + event_handled 멱등이 핵심.
 */
class RankingAggregatorTest {

    private static final JsonNodeFactory J = JsonNodeFactory.instance;
    private static final String KST_MIDNIGHT = "2026-07-14T00:00:00+09:00"; // → ranking:all:20260714
    private static final String KEY = "ranking:all:20260714";
    private static final String GROUP = "ranking-aggregator";

    private RankingRedisRepository repo;
    private EventHandledRepository eventHandled;
    private RankingAggregator aggregator;

    @BeforeEach
    void setUp() {
        repo = mock(RankingRedisRepository.class);
        eventHandled = mock(EventHandledRepository.class);
        // 기본값: 아무것도 처리된 적 없음(전부 fresh)
        when(eventHandled.findHandled(anyString(), any())).thenReturn(Set.of());
        aggregator = new RankingAggregator(repo, eventHandled);
    }

    private EventEnvelope viewed(long eventId, long productId, String occurredAt) {
        ObjectNode p = J.objectNode();
        p.put("productId", productId);
        return new EventEnvelope(eventId, "PRODUCT_VIEWED", "product", productId, 0L, occurredAt, p);
    }

    private EventEnvelope like(long eventId, long productId, long delta, String occurredAt) {
        ObjectNode p = J.objectNode();
        p.put("productId", productId);
        p.put("delta", delta);
        return new EventEnvelope(eventId, "LIKE_CHANGED", "product", productId, 0L, occurredAt, p);
    }

    private EventEnvelope orderPaid(long eventId, long productId, int quantity, long unitPrice, String occurredAt) {
        ObjectNode p = J.objectNode();
        p.put("orderId", 100L);
        ArrayNode items = p.putArray("items");
        ObjectNode item = items.addObject();
        item.put("productId", productId);
        item.put("quantity", quantity);
        item.put("unitPrice", unitPrice);
        return new EventEnvelope(eventId, "ORDER_PAID", "order", 100L, 0L, occurredAt, p);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Double>> capture() {
        ArgumentCaptor<Map<String, Map<String, Double>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(repo).incrementAll(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Collection<Long> captureMarked() {
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(eventHandled).markHandled(eq(GROUP), captor.capture());
        return captor.getValue();
    }

    @DisplayName("eventType별 스코어를 (일간 키, productId)로 합산해 incrementAll에 넘긴다.")
    @Test
    void aggregatesByType() {
        // 상품10: 조회 +0.1, 조회 +0.1, 좋아요 +0.2 = +0.4 (coalescing)
        // 상품20: 좋아요 취소 -0.2
        // 상품30: 주문(10,000 × 2) = 0.6·log10(1+20000)
        aggregator.apply(List.of(
                viewed(1L, 10, KST_MIDNIGHT),
                viewed(2L, 10, KST_MIDNIGHT),
                like(3L, 10, +1, KST_MIDNIGHT),
                like(4L, 20, -1, KST_MIDNIGHT),
                orderPaid(5L, 30, 2, 10_000, KST_MIDNIGHT)
        ));

        Map<String, Map<String, Double>> deltas = capture();
        assertThat(deltas).containsOnlyKeys(KEY);
        Map<String, Double> byProduct = deltas.get(KEY);
        assertThat(byProduct).containsOnlyKeys("10", "20", "30");
        assertThat(byProduct.get("10")).isCloseTo(0.4, within(1e-9));   // 두 조회 + 좋아요 합산
        assertThat(byProduct.get("20")).isCloseTo(-0.2, within(1e-9));  // 좋아요 취소 감점
        assertThat(byProduct.get("30")).isCloseTo(0.6 * Math.log10(1 + 20_000.0), within(1e-9));
    }

    @DisplayName("발생시각(occurredAt)이 다른 날이면 서로 다른 일간 키로 분리된다.")
    @Test
    void splitsByDateBucket() {
        aggregator.apply(List.of(
                viewed(1L, 10, "2026-07-14T00:00:00+09:00"),           // → 20260714
                viewed(2L, 10, "2026-07-13T23:59:59+09:00")            // → 20260713
        ));

        Map<String, Map<String, Double>> deltas = capture();
        assertThat(deltas).containsOnlyKeys("ranking:all:20260714", "ranking:all:20260713");
        assertThat(deltas.get("ranking:all:20260714").get("10")).isCloseTo(0.1, within(1e-9));
        assertThat(deltas.get("ranking:all:20260713").get("10")).isCloseTo(0.1, within(1e-9));
    }

    @DisplayName("빈 배치는 아무것도 반영하지 않는다.")
    @Test
    void noOpWhenEmpty() {
        aggregator.apply(List.of());

        verify(repo, never()).incrementAll(any());
        verify(eventHandled, never()).markHandled(anyString(), any());
    }

    @DisplayName("알 수 없는 eventType은 스코어에 반영하지 않되 handled로 마킹한다(무한 재조회 방지).")
    @Test
    void skipsUnknownTypeButMarksHandled() {
        ObjectNode p = J.objectNode();
        p.put("couponId", 1L);
        EventEnvelope unknown = new EventEnvelope(9L, "COUPON_ISSUE_REQUESTED", "coupon", 1L, 0L, KST_MIDNIGHT, p);

        aggregator.apply(List.of(unknown, viewed(10L, 10, KST_MIDNIGHT)));

        Map<String, Map<String, Double>> deltas = capture();
        assertThat(deltas.get(KEY)).containsOnlyKeys("10"); // 조회만 반영, 쿠폰 이벤트 무시
        assertThat(captureMarked()).containsExactlyInAnyOrder(9L, 10L); // 스킵한 것도 마킹
    }

    @DisplayName("배치 내 중복 eventId는 한 번만 반영한다(하이브리드 Outbox 이중 발행이 같은 배치에 실려도 안전).")
    @Test
    void dedupesDuplicateEventIdWithinBatch() {
        // 같은 좋아요 취소 이벤트(id=12)가 두 번 실려온 상황 — week9 E2E에서 실제로 관측된 형태
        aggregator.apply(List.of(
                viewed(11L, 3, KST_MIDNIGHT),
                like(12L, 3, -1, KST_MIDNIGHT),
                like(12L, 3, -1, KST_MIDNIGHT)
        ));

        Map<String, Map<String, Double>> deltas = capture();
        // 0.1(조회) - 0.2(취소 1회) = -0.1 이 아니라, 취소가 한 번만 반영되어야 한다
        assertThat(deltas.get(KEY).get("3")).isCloseTo(0.1 - 0.2, within(1e-9));
        assertThat(captureMarked()).containsExactlyInAnyOrder(11L, 12L); // 12는 하나로 접힘
    }

    @DisplayName("이미 처리한 eventId는 스코어에 반영하지 않는다(다른 배치로 재전달된 중복).")
    @Test
    void skipsAlreadyHandledEventId() {
        when(eventHandled.findHandled(eq(GROUP), any())).thenReturn(Set.of(12L));

        aggregator.apply(List.of(
                viewed(11L, 3, KST_MIDNIGHT),
                like(12L, 3, -1, KST_MIDNIGHT)   // 이전 배치에서 이미 반영됨
        ));

        Map<String, Map<String, Double>> deltas = capture();
        assertThat(deltas.get(KEY).get("3")).isCloseTo(0.1, within(1e-9)); // 조회만
        assertThat(captureMarked()).containsExactly(11L);
    }

    @DisplayName("배치가 전부 이미 처리된 이벤트면 Redis를 건드리지 않는다.")
    @Test
    void noRedisWriteWhenAllHandled() {
        when(eventHandled.findHandled(eq(GROUP), any())).thenReturn(Set.of(11L, 12L));

        aggregator.apply(List.of(
                viewed(11L, 3, KST_MIDNIGHT),
                like(12L, 3, -1, KST_MIDNIGHT)
        ));

        verify(repo, never()).incrementAll(any());
        verify(eventHandled, never()).markHandled(anyString(), any());
    }
}
