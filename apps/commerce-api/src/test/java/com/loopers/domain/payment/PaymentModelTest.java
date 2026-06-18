package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentModelTest {

    @Test
    @DisplayName("寃곗젣 ?앹꽦 ??二쇰Ц ?앸퀎?? 寃곗젣 ?섎떒, 湲덉븸, 寃곗젣 ?곹깭(APPROVED), ?뱀씤 ?쒓컖 諛?嫄곕옒 ?앸퀎?먭? ?ㅼ젙?쒕떎.")
    void constructor_ShouldInitializeFieldsCorrectly() {
        // given
        Long orderId = 100L;
        PaymentMethod method = PaymentMethod.CARD;
        BigDecimal amount = new BigDecimal("9000");
        String transactionId = "TX-12345";
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 11, 21, 0);

        // when
        PaymentModel payment = new PaymentModel(orderId, method, amount, transactionId, approvedAt);

        // then
        assertThat(payment.getOrderId()).isEqualTo(orderId);
        assertThat(payment.getMethod()).isEqualTo(method);
        assertThat(payment.getAmount()).isEqualTo(amount);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getTransactionId()).isEqualTo(transactionId);
        assertThat(payment.getApprovedAt()).isEqualTo(approvedAt);
    }
}
