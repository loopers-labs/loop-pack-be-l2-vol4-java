package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentGatewayTransactionTest {

    @DisplayName("주문의 PG 트랜잭션 목록에서 대표 결과를 고를 때, ")
    @Nested
    class ResolveFrom {

        @DisplayName("목록이 비어 있으면(PG 미접수), key 없는 FAILED 로 본다.")
        @Test
        void resolvesToFailed_whenEmpty() {
            // given
            // when
            PaymentGatewayTransaction resolved = PaymentGatewayTransaction.resolveFrom(List.of());

            // then
            assertThat(resolved.transactionKey()).isNull();
            assertThat(resolved.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(resolved.reason()).isEqualTo("PG 미접수");
        }

        @DisplayName("SUCCESS 가 있으면, 그 SUCCESS 트랜잭션을 채택한다.")
        @Test
        void resolvesToSuccess_whenAnySuccess() {
            // given
            List<PaymentGatewayTransaction> transactions = List.of(
                new PaymentGatewayTransaction("key-pending", PaymentStatus.PENDING, null),
                new PaymentGatewayTransaction("key-success", PaymentStatus.SUCCESS, "정상 승인")
            );

            // when
            PaymentGatewayTransaction resolved = PaymentGatewayTransaction.resolveFrom(transactions);

            // then
            assertThat(resolved.transactionKey()).isEqualTo("key-success");
            assertThat(resolved.status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("SUCCESS 가 여럿이면, 가장 앞(최신) SUCCESS 를 채택한다.")
        @Test
        void resolvesToFirstSuccess_whenMultipleSuccess() {
            // given
            List<PaymentGatewayTransaction> transactions = List.of(
                new PaymentGatewayTransaction("key-latest", PaymentStatus.SUCCESS, "최신 승인"),
                new PaymentGatewayTransaction("key-older", PaymentStatus.SUCCESS, "이전 승인")
            );

            // when
            PaymentGatewayTransaction resolved = PaymentGatewayTransaction.resolveFrom(transactions);

            // then
            assertThat(resolved.transactionKey()).isEqualTo("key-latest");
        }

        @DisplayName("SUCCESS 가 없으면, 가장 앞(최신) 트랜잭션을 채택한다.")
        @Test
        void resolvesToFirst_whenNoSuccess() {
            // given
            List<PaymentGatewayTransaction> transactions = List.of(
                new PaymentGatewayTransaction("key-pending", PaymentStatus.PENDING, null),
                new PaymentGatewayTransaction("key-failed", PaymentStatus.FAILED, "한도초과")
            );

            // when
            PaymentGatewayTransaction resolved = PaymentGatewayTransaction.resolveFrom(transactions);

            // then
            assertThat(resolved.transactionKey()).isEqualTo("key-pending");
            assertThat(resolved.status()).isEqualTo(PaymentStatus.PENDING);
        }
    }
}
