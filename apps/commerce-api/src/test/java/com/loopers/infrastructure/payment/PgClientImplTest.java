package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgClientException;
import com.loopers.domain.payment.PgConnectionException;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgPaymentResult;
import com.loopers.domain.payment.PgTransactionDetail;
import com.loopers.domain.payment.PgTransactionStatus;
import com.loopers.interfaces.api.ApiResponse;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.RetryableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("infra")
class PgClientImplTest {

    private final PgFeignClient pgFeignClient = mock(PgFeignClient.class);
    private final PgClientImpl pgClient = new PgClientImpl(pgFeignClient);

    private static final String USER_ID = "1";

    private static Request request() {
        return Request.create(
            Request.HttpMethod.POST,
            "http://localhost:8082/api/v1/payments",
            Collections.emptyMap(),
            null,
            StandardCharsets.UTF_8,
            new RequestTemplate()
        );
    }

    private static PgPaymentCommand command() {
        return new PgPaymentCommand(
            USER_ID,
            "1351039135",
            CardType.SAMSUNG,
            "1234-5678-9814-1451",
            5000L,
            "http://localhost:8080/api/v1/payments/callback"
        );
    }

    @Nested
    @DisplayName("결제 요청 시")
    class RequestPayment {

        @Test
        @DisplayName("PG 응답을 도메인 결과로 매핑한다")
        void mapsResponseToResult() {
            // arrange
            PgV1Dto.TransactionResponse response = new PgV1Dto.TransactionResponse("TR:abc", "PENDING", null);
            when(pgFeignClient.requestPayment(eq(USER_ID), any()))
                .thenReturn(ApiResponse.success(response));

            // act
            PgPaymentResult result = pgClient.requestPayment(command());

            // assert
            assertThat(result.transactionKey()).isEqualTo("TR:abc");
            assertThat(result.status()).isEqualTo(PgTransactionStatus.PENDING);
        }

        @Test
        @DisplayName("PG 호출이 실패하면 PgClientException 으로 변환한다")
        void translatesFeignFailure() {
            // arrange
            when(pgFeignClient.requestPayment(eq(USER_ID), any()))
                .thenThrow(new FeignException.InternalServerError("서버 불안정", request(), null, null));

            // act & assert
            assertThatThrownBy(() -> pgClient.requestPayment(command()))
                .isInstanceOf(PgClientException.class);
        }

        @Test
        @DisplayName("응답 본문이 비어 있으면 PgClientException 을 던진다")
        void throwsWhenBodyEmpty() {
            // arrange
            when(pgFeignClient.requestPayment(eq(USER_ID), any()))
                .thenReturn(ApiResponse.success(null));

            // act & assert
            assertThatThrownBy(() -> pgClient.requestPayment(command()))
                .isInstanceOf(PgClientException.class);
        }

        @Test
        @DisplayName("연결 실패(connection refused)는 재시도 가능한 PgConnectionException 으로 변환한다")
        void translatesConnectionRefusedAsRetryable() {
            // arrange
            RetryableException connectionRefused = new RetryableException(
                -1, "connection refused", Request.HttpMethod.POST, new ConnectException("refused"), (Long) null, request());
            when(pgFeignClient.requestPayment(eq(USER_ID), any())).thenThrow(connectionRefused);

            // act & assert
            assertThatThrownBy(() -> pgClient.requestPayment(command()))
                .isInstanceOf(PgConnectionException.class);
        }

        @Test
        @DisplayName("read timeout 은 재시도 불가한 일반 PgClientException 으로 변환한다(이중결제 방지)")
        void translatesReadTimeoutAsNonRetryable() {
            // arrange
            RetryableException readTimeout = new RetryableException(
                -1, "read timed out", Request.HttpMethod.POST, new SocketTimeoutException("Read timed out"), (Long) null, request());
            when(pgFeignClient.requestPayment(eq(USER_ID), any())).thenThrow(readTimeout);

            // act & assert
            assertThatThrownBy(() -> pgClient.requestPayment(command()))
                .isInstanceOf(PgClientException.class)
                .isNotInstanceOf(PgConnectionException.class);
        }
    }

    @Nested
    @DisplayName("결제 상세 조회 시")
    class GetTransaction {

        @Test
        @DisplayName("존재하면 도메인 상세로 매핑한다")
        void mapsDetail() {
            // arrange
            PgV1Dto.TransactionDetailResponse response = new PgV1Dto.TransactionDetailResponse(
                "TR:abc", "1351039135", "SAMSUNG", "1234-5678-9814-1451", 5000L, "SUCCESS", null
            );
            when(pgFeignClient.getTransaction(USER_ID, "TR:abc"))
                .thenReturn(ApiResponse.success(response));

            // act
            Optional<PgTransactionDetail> result = pgClient.getTransaction(USER_ID, "TR:abc");

            // assert
            assertThat(result).isPresent();
            assertThat(result.get().status()).isEqualTo(PgTransactionStatus.SUCCESS);
            assertThat(result.get().cardType()).isEqualTo(CardType.SAMSUNG);
        }

        @Test
        @DisplayName("존재하지 않으면(404) Optional.empty 를 반환한다")
        void returnsEmptyOnNotFound() {
            // arrange
            when(pgFeignClient.getTransaction(USER_ID, "TR:none"))
                .thenThrow(new FeignException.NotFound("없음", request(), null, null));

            // act
            Optional<PgTransactionDetail> result = pgClient.getTransaction(USER_ID, "TR:none");

            // assert
            assertThat(result).isEmpty();
        }
    }
}
