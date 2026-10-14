package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentServiceTest {

    private static final String CARD = "1234-5678-9012-3456";

    private FakePaymentRepository repository;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        repository = new FakePaymentRepository();
        service = new PaymentService(repository);
    }

    @DisplayName("createPending")
    @Nested
    class CreatePending {

        @DisplayName("처음 호출은 PENDING 결제를 새로 생성한다.")
        @Test
        void createsNew() {
            PaymentModel payment = service.createPending(1L, 10L, 5000L, CardType.SAMSUNG, CARD);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getOrderId()).isEqualTo(1L);
        }

        @DisplayName("같은 orderId 로 중복 호출 시, 기존 결제건이 반환된다 (UNIQUE 충돌 흡수).")
        @Test
        void returnsExistingOnDuplicate() {
            PaymentModel first = service.createPending(1L, 10L, 5000L, CardType.SAMSUNG, CARD);

            PaymentModel second = service.createPending(1L, 10L, 5000L, CardType.SAMSUNG, CARD);

            assertThat(second.getId()).isEqualTo(first.getId());
        }
    }

    @DisplayName("상태 전이")
    @Nested
    class Transitions {

        @DisplayName("markRequested 후 markSucceeded 가 정상 동작한다.")
        @Test
        void requestThenSucceed() {
            PaymentModel saved = service.createPending(1L, 10L, 5000L, CardType.SAMSUNG, CARD);

            service.markRequested(saved.getId(), "TX-001");
            service.markSucceeded(saved.getId());

            PaymentModel after = service.getById(saved.getId());
            assertThat(after.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(after.getTransactionKey()).isEqualTo("TX-001");
        }

        @DisplayName("존재하지 않는 결제 ID 로 전이 시 NOT_FOUND.")
        @Test
        void notFoundForMissingId() {
            CoreException ex = assertThrows(CoreException.class, () -> service.markRequested(999L, "TX"));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
