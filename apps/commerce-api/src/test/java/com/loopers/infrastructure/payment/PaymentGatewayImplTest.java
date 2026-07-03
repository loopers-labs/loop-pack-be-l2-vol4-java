package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGatewayException;
import com.loopers.domain.payment.PaymentGatewayTimeoutException;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgTransaction;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentGatewayImplTest {

    private static final PaymentGatewayProperties PROPS =
            new PaymentGatewayProperties("http://localhost:8082", "http://localhost:8080/api/v1/payments/callback", "loopers");
    private static final PgPaymentCommand COMMAND =
            new PgPaymentCommand("20260626000000000001", CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);

    private PaymentGatewayImpl adapter(StubFeignClient stub) {
        return new PaymentGatewayImpl(stub, PROPS);
    }

    @DisplayName("결제 요청 시 Feign 예외를 도메인 예외로 분기 변환할 때,")
    @Nested
    class RequestExceptionMapping {

        @DisplayName("5xx 응답은 PaymentGatewayException(미도달)으로 변환된다.")
        @Test
        void mapsServerErrorToGatewayException() {
            // given
            StubFeignClient stub = StubFeignClient.throwing(feignException(500));

            // when & then
            assertThrows(PaymentGatewayException.class, () -> adapter(stub).request(COMMAND));
        }

        @DisplayName("4xx 응답은 CoreException(BAD_REQUEST)으로 변환된다(우리 측 버그).")
        @Test
        void mapsClientErrorToCoreException() {
            // given
            StubFeignClient stub = StubFeignClient.throwing(feignException(400));

            // when
            CoreException result = assertThrows(CoreException.class, () -> adapter(stub).request(COMMAND));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("Read Timeout 은 PaymentGatewayTimeoutException(재시도 금지)으로 변환된다.")
        @Test
        void mapsReadTimeoutToTimeoutException() {
            // given
            StubFeignClient stub = StubFeignClient.throwing(
                    retryableException(new SocketTimeoutException("Read timed out")));

            // when & then
            assertThrows(PaymentGatewayTimeoutException.class, () -> adapter(stub).request(COMMAND));
        }

        @DisplayName("Connect Timeout/연결 실패는 PaymentGatewayException(미도달, 재시도 안전)으로 변환된다.")
        @Test
        void mapsConnectFailureToGatewayException() {
            // given
            StubFeignClient stub = StubFeignClient.throwing(retryableException(new ConnectException("Connection refused")));

            // when
            PaymentGatewayException result =
                    assertThrows(PaymentGatewayException.class, () -> adapter(stub).request(COMMAND));

            // then — 타임아웃 하위 타입이 아니어야 한다(재시도 대상)
            assertThat(result).isNotInstanceOf(PaymentGatewayTimeoutException.class);
        }
    }

    @DisplayName("PG 상태를 우리 PaymentStatus 로 매핑하고 조회할 때,")
    @Nested
    class StatusMappingAndQuery {

        @DisplayName("접수 응답(PENDING)은 PgTransaction(status=PENDING)으로 매핑된다.")
        @Test
        void mapsPendingOnRequest() {
            // given
            StubFeignClient stub = new StubFeignClient();
            stub.requestResponse = new PgApiResponse<>(meta(),
                    new PgTransactionResponse("20260626:TR:abc", "PENDING", null));

            // when
            PgTransaction tx = adapter(stub).request(COMMAND);

            // then
            assertAll(
                    () -> assertThat(tx.transactionKey()).isEqualTo("20260626:TR:abc"),
                    () -> assertThat(tx.status()).isEqualTo(PaymentStatus.PENDING)
            );
        }

        @DisplayName("단건 조회에서 SUCCESS 는 PAID 로 매핑되고 amount 가 채워진다.")
        @Test
        void mapsSuccessToPaidWithAmount() {
            // given
            StubFeignClient stub = new StubFeignClient();
            stub.detailResponse = new PgApiResponse<>(meta(),
                    new PgTransactionDetailResponse("20260626:TR:abc", "ORD1", "SAMSUNG",
                            "1234-5678-9814-1451", 5000L, "SUCCESS", "정상 승인"));

            // when
            Optional<PgTransaction> tx = adapter(stub).findByTransactionKey("20260626:TR:abc");

            // then
            assertAll(
                    () -> assertThat(tx).isPresent(),
                    () -> assertThat(tx.get().status()).isEqualTo(PaymentStatus.PAID),
                    () -> assertThat(tx.get().amount()).isEqualTo(5000L)
            );
        }

        @DisplayName("단건 조회 404(주문 없음)는 빈 Optional 로 반환된다(예외 아님).")
        @Test
        void returnsEmptyOnNotFound() {
            // given
            StubFeignClient stub = StubFeignClient.throwing(feignException(404));

            // when
            Optional<PgTransaction> tx = adapter(stub).findByTransactionKey("none");

            // then
            assertThat(tx).isEmpty();
        }

        @DisplayName("주문별 조회 404(주문 없음)는 빈 리스트로 반환된다(예외 아님).")
        @Test
        void returnsEmptyListOnNotFound() {
            // given
            StubFeignClient stub = StubFeignClient.throwing(feignException(404));

            // when
            List<PgTransaction> txs = adapter(stub).findByOrderId("none");

            // then
            assertThat(txs).isEmpty();
        }
    }

    // ---- helpers ----

    private static PgApiResponse.Metadata meta() {
        return new PgApiResponse.Metadata("SUCCESS", null, null);
    }

    private static Request dummyRequest() {
        return Request.create(Request.HttpMethod.POST, "http://localhost:8082/api/v1/payments",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
    }

    private static FeignException feignException(int status) {
        Response response = Response.builder()
                .status(status)
                .reason("error")
                .request(dummyRequest())
                .headers(Map.of())
                .build();
        return FeignException.errorStatus("PaymentGatewayFeignClient#request", response);
    }

    private static RetryableException retryableException(Throwable cause) {
        return new RetryableException(-1, cause.getMessage(), Request.HttpMethod.POST, cause, (Long) null, dummyRequest());
    }

    /** 도메인 예외 매핑만 검증하기 위한 수제 stub (Mockito any()/verify() 미사용). */
    static class StubFeignClient implements PaymentGatewayFeignClient {
        RuntimeException toThrow;
        PgApiResponse<PgTransactionResponse> requestResponse;
        PgApiResponse<PgTransactionDetailResponse> detailResponse;
        PgApiResponse<PgOrderResponse> orderResponse;

        static StubFeignClient throwing(RuntimeException e) {
            StubFeignClient stub = new StubFeignClient();
            stub.toThrow = e;
            return stub;
        }

        @Override
        public PgApiResponse<PgTransactionResponse> request(String userId, PgPaymentRequest body) {
            if (toThrow != null) throw toThrow;
            return requestResponse;
        }

        @Override
        public PgApiResponse<PgTransactionDetailResponse> getByKey(String userId, String transactionKey) {
            if (toThrow != null) throw toThrow;
            return detailResponse;
        }

        @Override
        public PgApiResponse<PgOrderResponse> getByOrderId(String userId, String orderId) {
            if (toThrow != null) throw toThrow;
            return orderResponse;
        }
    }
}
