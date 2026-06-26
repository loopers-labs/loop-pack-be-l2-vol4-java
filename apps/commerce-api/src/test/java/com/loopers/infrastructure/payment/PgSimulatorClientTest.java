package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgTransaction;
import com.loopers.infrastructure.payment.PgSimulatorFeignClient.PgOrderDto;
import com.loopers.infrastructure.payment.PgSimulatorFeignClient.PgPaymentRequestDto;
import com.loopers.infrastructure.payment.PgSimulatorFeignClient.PgTransactionDto;
import com.loopers.interfaces.api.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 어댑터의 변환 책임(도메인 VO ↔ pg-simulator DTO, orderId 패딩, ApiResponse 언랩)을 검증한다.
 * 재시도/서킷(@Retry/@CircuitBreaker)은 스프링 AOP 프록시가 필요해 여기서는 다루지 않는다.
 */
class PgSimulatorClientTest {

    private final PgSimulatorFeignClient feignClient = mock(PgSimulatorFeignClient.class);
    private final PgSimulatorClient client = new PgSimulatorClient(feignClient);

    @Nested
    @DisplayName("결제 요청 시")
    class RequestPayment {

        @Test
        @DisplayName("orderId(TSID)는 패딩 없이 문자열로, 원본 카드번호와 X-USER-ID 헤더가 그대로 전달된다")
        void given_request_when_pay_then_forwardsOrderIdAndRawCardNo() {
            when(feignClient.requestPayment(any(), any()))
                    .thenReturn(ApiResponse.success(new PgTransactionDto("20260622:TR:abc123", "PENDING", null)));

            client.requestPayment(new PgPaymentRequest(
                    1234567890123L, 42L, CardType.SAMSUNG, "1234-5678-9814-1451", 5000L,
                    "http://localhost:8080/api/v1/payments/callback"));

            ArgumentCaptor<String> userId = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<PgPaymentRequestDto> body = ArgumentCaptor.forClass(PgPaymentRequestDto.class);
            org.mockito.Mockito.verify(feignClient).requestPayment(userId.capture(), body.capture());

            assertThat(userId.getValue()).isEqualTo("42");
            assertThat(body.getValue().orderId()).isEqualTo("1234567890123");
            assertThat(body.getValue().cardType()).isEqualTo("SAMSUNG");
            assertThat(body.getValue().cardNo()).isEqualTo("1234-5678-9814-1451"); // 원본(마스킹 X)
            assertThat(body.getValue().amount()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("ApiResponse를 언랩해 transactionKey/상태를 도메인 VO로 매핑한다")
        void given_pgResponse_when_pay_then_mapsToTransaction() {
            when(feignClient.requestPayment(any(), any()))
                    .thenReturn(ApiResponse.success(new PgTransactionDto("20260622:TR:abc123", "PENDING", null)));

            PgTransaction tx = client.requestPayment(new PgPaymentRequest(
                    1L, 1L, CardType.KB, "1111-2222-3333-4444", 1000L, "http://localhost:8080/cb"));

            assertThat(tx.transactionKey()).isEqualTo("20260622:TR:abc123");
            assertThat(tx.status()).isEqualTo(PaymentStatus.PENDING);
            assertThat(tx.isPending()).isTrue();
        }
    }

    @Nested
    @DisplayName("주문별 거래 조회 시")
    class FindByOrder {

        @Test
        @DisplayName("거래 목록을 도메인 VO 리스트로 매핑한다")
        void given_transactions_when_find_then_mapsList() {
            when(feignClient.findTransactionsByOrder(eq("1"), eq("1")))
                    .thenReturn(ApiResponse.success(new PgOrderDto("1", List.of(
                            new PgTransactionDto("k1", "FAILED", "한도초과"),
                            new PgTransactionDto("k2", "SUCCESS", null)
                    ))));

            List<PgTransaction> result = client.findTransactionsByOrder(1L);

            assertThat(result).extracting(PgTransaction::status)
                    .containsExactly(PaymentStatus.FAILED, PaymentStatus.SUCCESS);
            assertThat(result.get(0).reason()).isEqualTo("한도초과");
        }

        @Test
        @DisplayName("거래가 없으면 빈 리스트를 반환한다")
        void given_noData_when_find_then_emptyList() {
            when(feignClient.findTransactionsByOrder(any(), any()))
                    .thenReturn(ApiResponse.success(new PgOrderDto("000001", null)));

            assertThat(client.findTransactionsByOrder(1L)).isEmpty();
        }
    }
}
