package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.GatewayLookup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PgPaymentGatewayTest {

    @Mock
    private PgClient pgClient;
    @InjectMocks
    private PgPaymentGateway paymentGateway;

    @DisplayName("주문 기준 거래 조회 시")
    @Nested
    class QueryByOrderId {

        @DisplayName("PG에 거래가 하나도 없으면 NOT_FOUND를 반환한다")
        @Test
        void returnsNotFound_whenNoTransaction() {
            when(pgClient.findByOrderId(1L, 10L)).thenReturn(List.of());

            GatewayLookup result = paymentGateway.queryByOrderId(1L, 10L);

            assertThat(result.result()).isEqualTo(GatewayLookup.Result.NOT_FOUND);
        }

        @DisplayName("이중 접수로 거래가 여러 개여도 SUCCESS 거래가 있으면 그 거래로 확정한다")
        @Test
        void picksSuccess_whenMultipleTransactions() {
            when(pgClient.findByOrderId(1L, 10L)).thenReturn(List.of(
                new PgTransactionResponse("tx-failed", "FAILED", "한도초과"),
                new PgTransactionResponse("tx-success", "SUCCESS", "정상 승인되었습니다.")
            ));

            GatewayLookup result = paymentGateway.queryByOrderId(1L, 10L);

            assertThat(result.result()).isEqualTo(GatewayLookup.Result.FOUND);
            assertThat(result.transactionKey()).isEqualTo("tx-success");
            assertThat(result.status()).isEqualTo("SUCCESS");
        }

        @DisplayName("SUCCESS가 없고 아직 처리 중(PENDING)이면 PENDING으로 두어 다음 주기를 기다린다")
        @Test
        void keepsPending_whenStillProcessing() {
            when(pgClient.findByOrderId(1L, 10L)).thenReturn(List.of(
                new PgTransactionResponse("tx-pending", "PENDING", null)
            ));

            GatewayLookup result = paymentGateway.queryByOrderId(1L, 10L);

            assertThat(result.result()).isEqualTo(GatewayLookup.Result.FOUND);
            assertThat(result.status()).isEqualTo("PENDING");
        }

        @DisplayName("거래가 전부 FAILED이면 실패 거래로 확정한다")
        @Test
        void picksFailed_whenAllFailed() {
            when(pgClient.findByOrderId(1L, 10L)).thenReturn(List.of(
                new PgTransactionResponse("tx-failed", "FAILED", "잘못된 카드입니다.")
            ));

            GatewayLookup result = paymentGateway.queryByOrderId(1L, 10L);

            assertThat(result.result()).isEqualTo(GatewayLookup.Result.FOUND);
            assertThat(result.status()).isEqualTo("FAILED");
            assertThat(result.reason()).isEqualTo("잘못된 카드입니다.");
        }
    }
}
