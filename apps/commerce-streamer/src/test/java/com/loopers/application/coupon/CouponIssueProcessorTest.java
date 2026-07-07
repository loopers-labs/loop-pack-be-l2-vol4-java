package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponIssueResultRepository;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.eventhandled.EventHandledRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponIssueProcessorTest {

    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final UserCouponRepository userCouponRepository = mock(UserCouponRepository.class);
    private final CouponIssueResultRepository resultRepository = mock(CouponIssueResultRepository.class);
    private final EventHandledRepository eventHandledRepository = mock(EventHandledRepository.class);
    private final CouponIssueProcessor processor = new CouponIssueProcessor(
        couponRepository, userCouponRepository, resultRepository, eventHandledRepository);

    private CouponIssueRequestMessage msg() {
        return new CouponIssueRequestMessage("req-1", 10L, 7L, "t");
    }

    private CouponIssueResult pending() {
        return CouponIssueResult.pending("req-1", 10L, 7L);
    }

    @DisplayName("정상: 중복아님 + 재고있음 → UserCoupon 발급 + result ISSUED.")
    @Test
    void issuesWhenAvailable() {
        when(eventHandledRepository.existsByEventId("req-1")).thenReturn(false);
        when(resultRepository.findByRequestId("req-1")).thenReturn(Optional.of(pending()));
        when(userCouponRepository.existsByUserIdAndCouponId(7L, 10L)).thenReturn(false);
        when(couponRepository.tryConsumeQuantity(10L)).thenReturn(1);
        when(couponRepository.find(10L)).thenReturn(Optional.of(mock(Coupon.class)));
        when(userCouponRepository.save(any(UserCoupon.class))).thenAnswer(inv -> inv.getArgument(0));

        processor.handle(msg());

        verify(userCouponRepository).save(any(UserCoupon.class));
        verify(eventHandledRepository).save(any());
    }

    @DisplayName("소진: tryConsumeQuantity==0 → REJECTED_SOLD_OUT, 발급 안 함.")
    @Test
    void rejectsWhenSoldOut() {
        when(eventHandledRepository.existsByEventId("req-1")).thenReturn(false);
        when(resultRepository.findByRequestId("req-1")).thenReturn(Optional.of(pending()));
        when(userCouponRepository.existsByUserIdAndCouponId(7L, 10L)).thenReturn(false);
        when(couponRepository.tryConsumeQuantity(10L)).thenReturn(0);

        processor.handle(msg());

        verify(userCouponRepository, never()).save(any());
    }

    @DisplayName("중복: 이미 발급 → REJECTED_DUPLICATE, 재고 차감 안 함.")
    @Test
    void rejectsDuplicate() {
        when(eventHandledRepository.existsByEventId("req-1")).thenReturn(false);
        when(resultRepository.findByRequestId("req-1")).thenReturn(Optional.of(pending()));
        when(userCouponRepository.existsByUserIdAndCouponId(7L, 10L)).thenReturn(true);

        processor.handle(msg());

        verify(couponRepository, never()).tryConsumeQuantity(any());
        verify(userCouponRepository, never()).save(any());
    }

    @DisplayName("멱등: 이미 처리한 requestId면 skip.")
    @Test
    void skipsWhenHandled() {
        when(eventHandledRepository.existsByEventId("req-1")).thenReturn(true);

        processor.handle(msg());

        verify(resultRepository, never()).findByRequestId(any());
        verify(couponRepository, never()).tryConsumeQuantity(any());
    }
}
