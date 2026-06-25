package com.loopers.application.payment;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.user.UserApplicationService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgTransactionResponse;
import com.loopers.domain.payment.PgTransactionStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class PaymentApplicationServiceIntegrationTest {

    @Autowired PaymentApplicationService paymentApplicationService;
    @Autowired UserApplicationService userApplicationService;
    @Autowired OrderApplicationService orderApplicationService;
    @Autowired BrandApplicationService brandApplicationService;
    @Autowired ProductApplicationService productApplicationService;
    @Autowired DatabaseCleanUp databaseCleanUp;
    @MockBean PgClient pgClient;

    private String userId;
    private String orderId;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @BeforeEach
    void setUp() {
        var user = userApplicationService.signup("testuser", "Password1!", "홍길동",
            LocalDate.of(1990, 1, 1), "test@test.com");
        userId = user.id();

        var brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
        var product = productApplicationService.createProduct(brand.id(), "에어맥스", "상품 설명", 100_000L, 10);
        var order = orderApplicationService.createOrder(userId, List.of(new OrderItemCommand(product.id(), 1)), null);
        orderId = order.orderId();
    }

    @DisplayName("initiate()")
    @Nested
    class Initiate {

        @DisplayName("PG 요청 성공 시 PaymentEntity가 PENDING으로 저장된다.")
        @Test
        void initiate_savesPaymentAsPending_whenPgRequestSucceeds() {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-001", PgTransactionStatus.PENDING, null));

            var future = paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            assertNotNull(future);
            assertFalse(future.isDone());
        }

        @DisplayName("PG 요청 실패 시 PaymentEntity가 FAILED로 저장되고 예외가 발생한다.")
        @Test
        void initiate_savesPaymentAsFailed_whenPgRequestFails() {
            when(pgClient.requestPayment(any(), any()))
                .thenThrow(new CoreException(ErrorType.PAYMENT_GATEWAY_ERROR, "PG 오류"));

            assertThrows(CoreException.class,
                () -> paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451"));
        }

        @DisplayName("동일 orderId에 PENDING 결제가 이미 있으면 예외가 발생한다.")
        @Test
        void initiate_throwsConflict_whenDuplicatePaymentExists() {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-001", PgTransactionStatus.PENDING, null));

            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            assertThrows(CoreException.class,
                () -> paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451"));
        }

        @DisplayName("PG가 즉시 FAILED를 응답하면 PaymentEntity가 FAILED로 확정되고 future도 FAILED로 완료된다.")
        @Test
        void initiate_completesAsFailed_whenPgRespondsFailedImmediately() throws Exception {
            // PG가 PENDING이 아닌 FAILED를 즉시 응답하면 applyPgResponse가 즉시 FAILED로 확정하고
            // initiate는 콜백 대기 없이 completedFuture를 반환한다. (getTransaction 미호출)
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-IMM-FAIL", PgTransactionStatus.FAILED, "한도 초과"));

            var future = paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            PaymentInfo result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.failureReason()).isEqualTo("한도 초과");
        }
    }

    @DisplayName("콜백 미수신 (timeout → 1차 Poll)")
    @Nested
    class CallbackTimeout {
        // 콜백을 의도적으로 보내지 않아 orTimeout(test 프로필 2s)이 발생하고,
        // exceptionally 블록의 1차 Poll(getTransaction mock)이 실행되는 경로를 검증한다.
        // future.get(5s)는 2s timeout 이후 Poll 결과로 완료되므로 5s 내에 반환된다.

        @DisplayName("timeout 후 1차 Poll이 SUCCESS면 future가 SUCCESS로 완료되고 주문이 PAID가 된다.")
        @Test
        void timeout_pollSuccess_completesAsSuccess() throws Exception {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-T1", PgTransactionStatus.PENDING, null));
            when(pgClient.getTransaction(eq("TX-T1"), any()))
                .thenReturn(new PgTransactionResponse("TX-T1", PgTransactionStatus.SUCCESS, null));

            var future = paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            PaymentInfo result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
            // (선택) 주문 조회 API가 있다면 OrderStatus.PAID 도 함께 검증
        }

        @DisplayName("timeout 후 1차 Poll이 FAILED면 future가 FAILED로 완료된다.")
        @Test
        void timeout_pollFailed_completesAsFailed() throws Exception {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-T2", PgTransactionStatus.PENDING, null));
            when(pgClient.getTransaction(eq("TX-T2"), any()))
                .thenReturn(new PgTransactionResponse("TX-T2", PgTransactionStatus.FAILED, "한도 초과"));

            var future = paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            PaymentInfo result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.failureReason()).isEqualTo("한도 초과");
        }

        @DisplayName("timeout 후 1차 Poll이 여전히 PENDING이면 future가 PENDING으로 완료된다. (Scheduler 후속 처리)")
        @Test
        void timeout_pollPending_completesAsPending() throws Exception {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-T3", PgTransactionStatus.PENDING, null));
            when(pgClient.getTransaction(eq("TX-T3"), any()))
                .thenReturn(new PgTransactionResponse("TX-T3", PgTransactionStatus.PENDING, null));

            var future = paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            PaymentInfo result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("timeout 후 1차 Poll 자체가 PG_QUERY_ERROR로 실패하면 예외를 삼키고 PENDING으로 완료된다.")
        @Test
        void timeout_pollThrows_completesAsPending() throws Exception {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-T4", PgTransactionStatus.PENDING, null));
            when(pgClient.getTransaction(eq("TX-T4"), any()))
                .thenThrow(new CoreException(ErrorType.PG_QUERY_ERROR, "PG 조회 실패"));

            var future = paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            PaymentInfo result = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

            assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        }
    }

    @DisplayName("processCallback()")
    @Nested
    class ProcessCallback {

        @DisplayName("SUCCESS 콜백 수신 시 PaymentEntity가 SUCCESS, OrderEntity가 PAID가 된다.")
        @Test
        void processCallback_updatesStatusToSuccess_andPaysOrder() {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-001", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            paymentApplicationService.processCallback("TX-001", PgTransactionStatus.SUCCESS, null);

            PaymentInfo payment = paymentApplicationService.getPayment(userId,
                getPaymentIdByTransactionKey("TX-001"));
            assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);

            var order = orderApplicationService.getOrder(userId, orderId);
            assertThat(order.status()).isEqualTo(OrderStatus.PAID);
        }

        @DisplayName("FAILED 콜백 수신 시 PaymentEntity가 FAILED가 된다.")
        @Test
        void processCallback_updatesStatusToFailed_whenPgFails() {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-001", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            paymentApplicationService.processCallback("TX-001", PgTransactionStatus.FAILED, "한도 초과");

            PaymentInfo payment = paymentApplicationService.getPayment(userId,
                getPaymentIdByTransactionKey("TX-001"));
            assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.failureReason()).isEqualTo("한도 초과");
        }

        @DisplayName("동일 transactionKey로 SUCCESS 콜백을 2회 수신해도 멱등하게 SUCCESS를 유지한다.")
        @Test
        void processCallback_isIdempotent_whenSuccessReceivedTwice() {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-IDEM", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            paymentApplicationService.processCallback("TX-IDEM", PgTransactionStatus.SUCCESS, null);
            assertDoesNotThrow(() ->
                paymentApplicationService.processCallback("TX-IDEM", PgTransactionStatus.SUCCESS, null));

            PaymentInfo payment = paymentApplicationService.getPaymentByTransactionKey("TX-IDEM");
            assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("1차 Poll이 SUCCESS로 확정한 뒤 늦은 SUCCESS 콜백이 도착해도 멱등하게 SUCCESS를 유지한다.")
        @Test
        void processCallback_isIdempotent_whenLateCallbackAfterPoll() throws Exception {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-RACE", PgTransactionStatus.PENDING, null));
            when(pgClient.getTransaction(eq("TX-RACE"), any()))
                .thenReturn(new PgTransactionResponse("TX-RACE", PgTransactionStatus.SUCCESS, null));

            // 콜백 미수신 → timeout(2s) 후 1차 Poll이 SUCCESS로 확정
            var future = paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            future.get(5, java.util.concurrent.TimeUnit.SECONDS);

            // 뒤늦게 SUCCESS 콜백이 도착
            assertDoesNotThrow(() ->
                paymentApplicationService.processCallback("TX-RACE", PgTransactionStatus.SUCCESS, null));

            PaymentInfo payment = paymentApplicationService.getPaymentByTransactionKey("TX-RACE");
            assertThat(payment.status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("존재하지 않는 transactionKey로 콜백을 수신하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void processCallback_throwsNotFound_whenTransactionKeyUnknown() {
            var exception = assertThrows(CoreException.class,
                () -> paymentApplicationService.processCallback("UNKNOWN", PgTransactionStatus.SUCCESS, null));
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("getPayment()")
    @Nested
    class GetPayment {

        @DisplayName("DB가 PENDING이면 PgClient를 직접 조회하여 상태를 갱신한다.")
        @Test
        void getPayment_pollsPg_whenStatusIsPending() {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-001", PgTransactionStatus.PENDING, null));
            var future = paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            String paymentId = getPaymentIdByTransactionKey("TX-001");

            when(pgClient.getTransaction(eq("TX-001"), any()))
                .thenReturn(new PgTransactionResponse("TX-001", PgTransactionStatus.SUCCESS, null));

            PaymentInfo result = paymentApplicationService.getPayment(userId, paymentId);

            assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        }

        @DisplayName("DB가 PENDING이고 PG Poll이 FAILED면 FAILED로 갱신하여 반환한다.")
        @Test
        void getPayment_updatesToFailed_whenPollFailed() {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-005", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            String paymentId = getPaymentIdByTransactionKey("TX-005");

            when(pgClient.getTransaction(eq("TX-005"), any()))
                .thenReturn(new PgTransactionResponse("TX-005", PgTransactionStatus.FAILED, "한도 초과"));

            PaymentInfo result = paymentApplicationService.getPayment(userId, paymentId);

            assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.failureReason()).isEqualTo("한도 초과");
        }

        @DisplayName("DB가 PENDING이고 PG 조회가 PG_QUERY_ERROR로 실패하면 예외가 그대로 전파된다.")
        @Test
        void getPayment_throwsPgQueryError_whenPgQueryFails() {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-006", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            String paymentId = getPaymentIdByTransactionKey("TX-006");

            when(pgClient.getTransaction(eq("TX-006"), any()))
                .thenThrow(new CoreException(ErrorType.PG_QUERY_ERROR, "PG 조회 실패"));

            var exception = assertThrows(CoreException.class,
                () -> paymentApplicationService.getPayment(userId, paymentId));
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.PG_QUERY_ERROR);
        }

        @DisplayName("소유자가 아닌 유저가 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void getPayment_throwsNotFound_whenNotOwner() {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-001", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            String paymentId = getPaymentIdByTransactionKey("TX-001");

            var exception = assertThrows(CoreException.class,
                () -> paymentApplicationService.getPayment("999", paymentId));
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("DB가 이미 SUCCESS로 확정된 결제는 PG 조회 없이 그대로 반환한다.")
        @Test
        void getPayment_doesNotPollPg_whenAlreadyConfirmed() {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-CONFIRMED", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            paymentApplicationService.processCallback("TX-CONFIRMED", PgTransactionStatus.SUCCESS, null);
            String paymentId = getPaymentIdByTransactionKey("TX-CONFIRMED");

            PaymentInfo result = paymentApplicationService.getPayment(userId, paymentId);

            assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
            verify(pgClient, never()).getTransaction(eq("TX-CONFIRMED"), any());
        }
    }

    private String getPaymentIdByTransactionKey(String transactionKey) {
        return paymentApplicationService.getPaymentByTransactionKey(transactionKey).paymentId();
    }
}
