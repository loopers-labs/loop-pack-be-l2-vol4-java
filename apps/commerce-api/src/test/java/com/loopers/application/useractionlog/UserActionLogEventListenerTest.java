package com.loopers.application.useractionlog;

import com.loopers.domain.order.event.OrderPlacedEvent;
import com.loopers.domain.payment.event.PaymentCompletedEvent;
import com.loopers.domain.payment.event.PaymentFailedEvent;
import com.loopers.domain.useractionlog.UserActionLogModel;
import com.loopers.domain.useractionlog.UserActionLogRepository;
import com.loopers.domain.useractionlog.UserActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class UserActionLogEventListenerTest {

    @InjectMocks
    private UserActionLogEventListener listener;

    @Mock private UserActionLogRepository userActionLogRepository;

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 10L;
    private static final Long PAYMENT_ID = 100L;

    @DisplayName("OrderPlacedEvent 수신 시,")
    @Nested
    class OnOrderPlaced {

        @DisplayName("ORDER_PLACED 유형의 로그가 저장된다.")
        @Test
        void savesOrderPlacedLog() {
            // arrange
            ArgumentCaptor<UserActionLogModel> captor = ArgumentCaptor.forClass(UserActionLogModel.class);
            given(userActionLogRepository.save(captor.capture())).willReturn(null);

            // act
            listener.on(new OrderPlacedEvent(ORDER_ID, USER_ID));

            // assert
            UserActionLogModel saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getActionType()).isEqualTo(UserActionType.ORDER_PLACED);
            assertThat(saved.getReferenceId()).isEqualTo(ORDER_ID);
        }

        @DisplayName("저장 중 예외가 발생해도 상위로 전파되지 않는다 (주문은 이미 커밋된 상태).")
        @Test
        void doesNotPropagateException_whenSaveFails() {
            // arrange
            willThrow(new RuntimeException("DB 오류")).given(userActionLogRepository).save(any());

            // act & assert
            assertDoesNotThrow(() -> listener.on(new OrderPlacedEvent(ORDER_ID, USER_ID)));
        }
    }

    @DisplayName("PaymentCompletedEvent 수신 시,")
    @Nested
    class OnPaymentCompleted {

        @DisplayName("PAYMENT_COMPLETED 유형의 로그가 저장된다.")
        @Test
        void savesPaymentCompletedLog() {
            // arrange
            ArgumentCaptor<UserActionLogModel> captor = ArgumentCaptor.forClass(UserActionLogModel.class);
            given(userActionLogRepository.save(captor.capture())).willReturn(null);

            // act
            listener.on(new PaymentCompletedEvent(PAYMENT_ID, ORDER_ID, USER_ID, List.of()));

            // assert
            UserActionLogModel saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getActionType()).isEqualTo(UserActionType.PAYMENT_COMPLETED);
            assertThat(saved.getReferenceId()).isEqualTo(PAYMENT_ID);
        }
    }

    @DisplayName("PaymentFailedEvent 수신 시,")
    @Nested
    class OnPaymentFailed {

        @DisplayName("PAYMENT_FAILED 유형의 로그가 failureCode와 함께 저장된다.")
        @Test
        void savesPaymentFailedLogWithFailureCode() {
            // arrange
            ArgumentCaptor<UserActionLogModel> captor = ArgumentCaptor.forClass(UserActionLogModel.class);
            given(userActionLogRepository.save(captor.capture())).willReturn(null);

            // act
            listener.on(new PaymentFailedEvent(PAYMENT_ID, ORDER_ID, USER_ID, "LIMIT_EXCEEDED"));

            // assert
            UserActionLogModel saved = captor.getValue();
            assertThat(saved.getActionType()).isEqualTo(UserActionType.PAYMENT_FAILED);
            assertThat(saved.getReferenceId()).isEqualTo(PAYMENT_ID);
            assertThat(saved.getDetail()).isEqualTo("LIMIT_EXCEEDED");
        }

        @DisplayName("저장 중 예외가 발생해도 상위로 전파되지 않는다 (결제 실패 처리는 이미 커밋된 상태).")
        @Test
        void doesNotPropagateException_whenSaveFails() {
            // arrange
            willThrow(new RuntimeException("DB 오류")).given(userActionLogRepository).save(any());

            // act & assert
            assertDoesNotThrow(() -> listener.on(new PaymentFailedEvent(PAYMENT_ID, ORDER_ID, USER_ID, "LIMIT_EXCEEDED")));
        }
    }
}
