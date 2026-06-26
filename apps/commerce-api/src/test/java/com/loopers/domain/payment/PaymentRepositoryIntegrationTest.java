package com.loopers.domain.payment;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class PaymentRepositoryIntegrationTest {

    private final PaymentRepository paymentRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    PaymentRepositoryIntegrationTest(PaymentRepository paymentRepository, DatabaseCleanUp databaseCleanUp) {
        this.paymentRepository = paymentRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("최종 상태 확정은 PENDING 결제에만 반영되어, 뒤늦은 stale 갱신이 기존 최종 상태를 덮어쓰지 못한다.")
    @Test
    void completesOnlyPendingPayment_whenStaleFinalStatusUpdateArrives() {
        // arrange
        paymentRepository.save(new Payment(
            "user1234",
            1L,
            PaymentCardType.SAMSUNG,
            "1234-5678-9814-1451",
            5_000L
        ));
        Payment callbackView = paymentRepository.findByOrderId(1L).orElseThrow();
        Payment lookupView = paymentRepository.findByOrderId(1L).orElseThrow();

        // act
        callbackView.applyGatewayResult(PaymentGatewayResult.success(
            "20260625:TR:callback",
            "정상 승인되었습니다."
        ));
        paymentRepository.completeIfPending(callbackView);

        lookupView.applyGatewayResult(PaymentGatewayResult.failed(
            "20260625:TR:lookup",
            "PG 조회 결과 실패입니다."
        ));
        Payment result = paymentRepository.completeIfPending(lookupView);

        // assert
        Payment savedPayment = paymentRepository.findByOrderId(1L).orElseThrow();
        assertAll(
            () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.PAID),
            () -> assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PAID),
            () -> assertThat(savedPayment.getPendingReason()).isNull(),
            () -> assertThat(savedPayment.getTransactionKey()).isEqualTo("20260625:TR:callback"),
            () -> assertThat(savedPayment.getReason()).isEqualTo("정상 승인되었습니다.")
        );
    }
}
