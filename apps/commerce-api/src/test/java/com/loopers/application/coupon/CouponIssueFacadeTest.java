package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponIssueResultRepository;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.event.CouponIssueRequested;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponIssueFacadeTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final CouponIssueResultRepository resultRepository = mock(CouponIssueResultRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final CouponIssueFacade facade =
        new CouponIssueFacade(userRepository, couponRepository, resultRepository, eventPublisher);

    private void givenUser(long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(userRepository.findByLoginId("tester01")).thenReturn(Optional.of(user));
    }

    private Coupon firstComeCoupon() {
        return new Coupon("선착순", CouponType.FIXED, 1000L, null, ZonedDateTime.now().plusDays(1), 100L);
    }

    @DisplayName("선착순 쿠폰 발급요청 시 PENDING 결과 저장 + CouponIssueRequested 발행 + requestId 반환.")
    @Test
    void requestIssue_savesPendingAndPublishes() {
        givenUser(7L);
        when(couponRepository.find(10L)).thenReturn(Optional.of(firstComeCoupon()));
        when(resultRepository.save(any(CouponIssueResult.class))).thenAnswer(inv -> inv.getArgument(0));

        String requestId = facade.requestIssue("tester01", 10L);

        assertThat(requestId).isNotBlank();
        verify(resultRepository).save(any(CouponIssueResult.class));
        ArgumentCaptor<CouponIssueRequested> captor = ArgumentCaptor.forClass(CouponIssueRequested.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().couponId()).isEqualTo(10L);
        assertThat(captor.getValue().userId()).isEqualTo(7L);
        assertThat(captor.getValue().requestId()).isEqualTo(requestId);
    }

    @DisplayName("선착순이 아닌(quantity=null) 쿠폰이면 BAD_REQUEST.")
    @Test
    void requestIssue_rejectsNonFirstCome() {
        givenUser(7L);
        Coupon normal = new Coupon("일반", CouponType.FIXED, 1000L, null, ZonedDateTime.now().plusDays(1));
        when(couponRepository.find(10L)).thenReturn(Optional.of(normal));

        CoreException ex = assertThrows(CoreException.class, () -> facade.requestIssue("tester01", 10L));
        assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}
