package com.loopers.application.coupon;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loopers.application.metrics.EventEnvelope;
import com.loopers.infrastructure.coupon.CouponIssueUpdater;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 선착순 발급 처리 단위 테스트 (Slice 4) — Kafka/DB 없이 검증한다.
 * 원자 예약 결과(1=발급 / 0=소진)별 분기 + event_handled 멱등 필터 + fresh 마킹이 핵심.
 */
class CouponIssuerTest {

    private static final JsonNodeFactory J = JsonNodeFactory.instance;

    private EventEnvelope issueRequested(long eventId, long requestId, long userId, long couponId) {
        ObjectNode p = J.objectNode();
        p.put("requestId", requestId);
        p.put("userId", userId);
        p.put("couponId", couponId);
        return new EventEnvelope(eventId, "COUPON_ISSUE_REQUESTED", "coupon", couponId, 0L, "2026-07-02T00:00:00Z", p);
    }

    @DisplayName("수량 확보 성공(reserve=1)이면 user_coupon 발급 + 요청 ISSUED로 확정하고 fresh eventId를 마킹한다.")
    @Test
    void issued_whenReserveSucceeds() {
        EventHandledRepository handled = mock(EventHandledRepository.class);
        CouponIssueUpdater updater = mock(CouponIssueUpdater.class);
        when(handled.findHandled(any(), any())).thenReturn(Set.of());
        when(updater.reserve(50L)).thenReturn(1);
        CouponIssuer issuer = new CouponIssuer(handled, updater);

        issuer.apply(List.of(issueRequested(1L, 900L, 100L, 50L)));

        verify(updater).reserve(50L);
        verify(updater).insertUserCoupon(100L, 50L);
        verify(updater).markRequest(900L, "ISSUED");
        verify(handled).markHandled(eq(CouponIssuer.CONSUMER_GROUP), any());
    }

    @DisplayName("수량 소진(reserve=0)이면 발급 없이 요청을 SOLD_OUT으로 확정한다.")
    @Test
    void soldOut_whenReserveFails() {
        EventHandledRepository handled = mock(EventHandledRepository.class);
        CouponIssueUpdater updater = mock(CouponIssueUpdater.class);
        when(handled.findHandled(any(), any())).thenReturn(Set.of());
        when(updater.reserve(50L)).thenReturn(0);
        CouponIssuer issuer = new CouponIssuer(handled, updater);

        issuer.apply(List.of(issueRequested(1L, 900L, 100L, 50L)));

        verify(updater).reserve(50L);
        verify(updater, never()).insertUserCoupon(anyLong(), anyLong());
        verify(updater).markRequest(900L, "SOLD_OUT");
        verify(handled).markHandled(eq(CouponIssuer.CONSUMER_GROUP), any());
    }

    @DisplayName("이미 처리된 eventId는 건너뛴다(멱등) — 발급도 마킹도 하지 않는다.")
    @Test
    void skipsAlreadyHandled() {
        EventHandledRepository handled = mock(EventHandledRepository.class);
        CouponIssueUpdater updater = mock(CouponIssueUpdater.class);
        when(handled.findHandled(any(), any())).thenReturn(Set.of(1L));
        CouponIssuer issuer = new CouponIssuer(handled, updater);

        issuer.apply(List.of(issueRequested(1L, 900L, 100L, 50L)));

        verify(updater, never()).reserve(anyLong());
        verify(handled, never()).markHandled(any(), any());
    }

    @DisplayName("배치 내 같은 eventId 중복은 한 번만 처리한다.")
    @Test
    void dedupesWithinBatch() {
        EventHandledRepository handled = mock(EventHandledRepository.class);
        CouponIssueUpdater updater = mock(CouponIssueUpdater.class);
        when(handled.findHandled(any(), any())).thenReturn(Set.of());
        when(updater.reserve(50L)).thenReturn(1);
        CouponIssuer issuer = new CouponIssuer(handled, updater);

        issuer.apply(List.of(
                issueRequested(1L, 900L, 100L, 50L),
                issueRequested(1L, 900L, 100L, 50L) // 같은 eventId 재전달
        ));

        verify(updater).reserve(50L); // 한 번만
        verify(updater).insertUserCoupon(100L, 50L);
    }
}
