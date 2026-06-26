package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentGateway;
import com.loopers.application.payment.PaymentGatewayCommand;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.payment.PaymentGatewayResult;
import com.loopers.domain.payment.PaymentPendingReason;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.order.OrderJpaEntity;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserDto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "loopers.payment.lookup-empty-failure-delay=0s"
)
class PaymentV1ApiE2ETest {

    private static final String ENDPOINT_PAYMENTS = "/api/v1/payments";
    private static final String ENDPOINT_SIGNUP = "/api/v1/users";

    private final TestRestTemplate testRestTemplate;
    private final OrderJpaRepository orderJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final CountingPaymentGateway paymentGateway;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    PaymentV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        OrderJpaRepository orderJpaRepository,
        PaymentJpaRepository paymentJpaRepository,
        CountingPaymentGateway paymentGateway,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.orderJpaRepository = orderJpaRepository;
        this.paymentJpaRepository = paymentJpaRepository;
        this.paymentGateway = paymentGateway;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        paymentGateway.reset();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    class RequestPayment {

        @DisplayName("주문에 대해 결제를 요청하면, 주문 최종 금액으로 PG 결제를 요청하고 PENDING 결제를 생성한다.")
        @Test
        void createsPendingPayment_whenPaymentIsRequested() {
            // arrange
            signup("user1234", "abc123!?");
            OrderJpaEntity order = saveOrder("user1234", 5_000L);
            PaymentDto.RequestPayment.V1.Request request = new PaymentDto.RequestPayment.V1.Request(
                order.getId(),
                com.loopers.domain.payment.PaymentCardType.SAMSUNG,
                "1234-5678-9814-1451"
            );

            // act
            ResponseEntity<ApiResponse<PaymentDto.RequestPayment.V1.Response>> response =
                testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS,
                    HttpMethod.POST,
                    new HttpEntity<>(request, authHeaders("user1234", "abc123!?")),
                    paymentResponseType()
                );

            // assert
            PaymentDto.RequestPayment.V1.Response data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.userLoginId()).isEqualTo("user1234"),
                () -> assertThat(data.orderId()).isEqualTo(order.getId()),
                () -> assertThat(data.amount()).isEqualTo(5_000L),
                () -> assertThat(data.status()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(data.pendingReason()).isEqualTo(PaymentPendingReason.WAITING_CALLBACK),
                () -> assertThat(data.transactionKey()).isEqualTo("20260625:TR:test")
            );
        }

        @DisplayName("같은 주문으로 결제 요청을 다시 보내면, PG 결제 생성 요청을 반복하지 않고 기존 결제를 반환한다.")
        @Test
        void returnsExistingPayment_withoutRequestingPgAgain_whenSameOrderPaymentIsRetried() {
            // arrange
            signup("user1234", "abc123!?");
            OrderJpaEntity order = saveOrder("user1234", 5_000L);
            PaymentDto.RequestPayment.V1.Request request = new PaymentDto.RequestPayment.V1.Request(
                order.getId(),
                com.loopers.domain.payment.PaymentCardType.SAMSUNG,
                "1234-5678-9814-1451"
            );

            // act
            ResponseEntity<ApiResponse<PaymentDto.RequestPayment.V1.Response>> firstResponse =
                requestPayment("user1234", "abc123!?", request);
            ResponseEntity<ApiResponse<PaymentDto.RequestPayment.V1.Response>> retryResponse =
                requestPayment("user1234", "abc123!?", request);

            // assert
            PaymentDto.RequestPayment.V1.Response firstData = firstResponse.getBody().data();
            PaymentDto.RequestPayment.V1.Response retryData = retryResponse.getBody().data();
            assertAll(
                () -> assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(firstData.id()).isEqualTo(retryData.id()),
                () -> assertThat(firstData.transactionKey()).isEqualTo(retryData.transactionKey()),
                () -> assertThat(paymentGateway.requestCount()).isEqualTo(1),
                () -> assertThat(paymentGateway.getByOrderCount()).isZero(),
                () -> assertThat(paymentJpaRepository.count()).isEqualTo(1)
            );
        }

        @DisplayName("같은 주문으로 결제 요청이 동시에 들어와도, PG 결제 생성 요청은 한 번만 보내고 같은 결제를 반환한다.")
        @Test
        void returnsExistingPayment_withoutRequestingPgAgain_whenSameOrderPaymentIsRequestedConcurrently()
            throws InterruptedException {
            // arrange
            signup("user1234", "abc123!?");
            OrderJpaEntity order = saveOrder("user1234", 5_000L);
            paymentGateway.setRequestDelayMillis(300);
            int threadCount = 5;
            PaymentDto.RequestPayment.V1.Request request = new PaymentDto.RequestPayment.V1.Request(
                order.getId(),
                com.loopers.domain.payment.PaymentCardType.SAMSUNG,
                "1234-5678-9814-1451"
            );

            // act
            List<ResponseEntity<ApiResponse<PaymentDto.RequestPayment.V1.Response>>> responses =
                requestPaymentConcurrently(threadCount, "user1234", "abc123!?", request);

            // assert
            List<Long> paymentIds = responses.stream()
                .map(response -> response.getBody().data().id())
                .distinct()
                .toList();
            assertAll(
                () -> assertThat(responses).hasSize(threadCount),
                () -> assertThat(responses).allSatisfy(response ->
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK)
                ),
                () -> assertThat(paymentIds).containsExactly(responses.get(0).getBody().data().id()),
                () -> assertThat(paymentGateway.requestCount()).isEqualTo(1),
                () -> assertThat(paymentJpaRepository.count()).isEqualTo(1)
            );
        }

        @DisplayName("PG 생성 요청 결과를 확정하지 못한 주문도 POST 재시도로 PG 생성을 반복하지 않고, 상태 조회로 복구한다.")
        @Test
        void recoversByStatusLookup_withoutRetryingPaymentCreation_whenInitialRequestIsUnknown() {
            // arrange
            signup("user1234", "abc123!?");
            OrderJpaEntity order = saveOrder("user1234", 5_000L);
            paymentGateway.setRequestResult(PaymentGatewayResult.pending(
                null,
                PaymentPendingReason.TIMEOUT_UNKNOWN,
                "PG 요청 결과를 확인하지 못했습니다: Read timed out"
            ));
            paymentGateway.setLookupResult(PaymentGatewayResult.success(
                "20260625:TR:recovered",
                "정상 승인되었습니다."
            ));
            PaymentDto.RequestPayment.V1.Request request = new PaymentDto.RequestPayment.V1.Request(
                order.getId(),
                com.loopers.domain.payment.PaymentCardType.SAMSUNG,
                "1234-5678-9814-1451"
            );

            // act
            ResponseEntity<ApiResponse<PaymentDto.RequestPayment.V1.Response>> firstResponse =
                requestPayment("user1234", "abc123!?", request);
            ResponseEntity<ApiResponse<PaymentDto.RequestPayment.V1.Response>> retryResponse =
                requestPayment("user1234", "abc123!?", request);
            ResponseEntity<ApiResponse<PaymentDto.RequestPayment.V1.Response>> lookupResponse =
                testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS + "/orders/" + order.getId(),
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders("user1234", "abc123!?")),
                    paymentResponseType()
                );

            // assert
            PaymentDto.RequestPayment.V1.Response firstData = firstResponse.getBody().data();
            PaymentDto.RequestPayment.V1.Response retryData = retryResponse.getBody().data();
            PaymentDto.RequestPayment.V1.Response lookupData = lookupResponse.getBody().data();
            assertAll(
                () -> assertThat(firstData.status()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(firstData.pendingReason()).isEqualTo(PaymentPendingReason.TIMEOUT_UNKNOWN),
                () -> assertThat(retryData.id()).isEqualTo(firstData.id()),
                () -> assertThat(retryData.pendingReason()).isEqualTo(PaymentPendingReason.TIMEOUT_UNKNOWN),
                () -> assertThat(lookupResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(lookupData.status()).isEqualTo(PaymentStatus.PAID),
                () -> assertThat(lookupData.pendingReason()).isNull(),
                () -> assertThat(lookupData.transactionKey()).isEqualTo("20260625:TR:recovered"),
                () -> assertThat(paymentGateway.requestCount()).isEqualTo(1),
                () -> assertThat(paymentGateway.getByOrderCount()).isEqualTo(1),
                () -> assertThat(paymentJpaRepository.count()).isEqualTo(1)
            );
        }

        @DisplayName("PG 상태 조회에서도 거래가 없고 유예시간이 지났으면 결제를 FAILED로 확정한다.")
        @Test
        void marksPaymentFailed_whenLookupEmptyGracePeriodHasElapsed() {
            // arrange
            signup("user1234", "abc123!?");
            OrderJpaEntity order = saveOrder("user1234", 5_000L);
            paymentGateway.setRequestResult(PaymentGatewayResult.pending(
                null,
                PaymentPendingReason.TIMEOUT_UNKNOWN,
                "PG 요청 결과를 확인하지 못했습니다: Read timed out"
            ));
            paymentGateway.setLookupResult(PaymentGatewayResult.pending(
                null,
                PaymentPendingReason.PG_LOOKUP_EMPTY,
                "PG에 해당 주문 결제가 없습니다."
            ));
            PaymentDto.RequestPayment.V1.Request request = new PaymentDto.RequestPayment.V1.Request(
                order.getId(),
                com.loopers.domain.payment.PaymentCardType.SAMSUNG,
                "1234-5678-9814-1451"
            );

            // act
            ResponseEntity<ApiResponse<PaymentDto.RequestPayment.V1.Response>> firstResponse =
                requestPayment("user1234", "abc123!?", request);
            ResponseEntity<ApiResponse<PaymentDto.RequestPayment.V1.Response>> lookupResponse =
                testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS + "/orders/" + order.getId(),
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders("user1234", "abc123!?")),
                    paymentResponseType()
                );

            // assert
            PaymentDto.RequestPayment.V1.Response firstData = firstResponse.getBody().data();
            PaymentDto.RequestPayment.V1.Response lookupData = lookupResponse.getBody().data();
            assertAll(
                () -> assertThat(firstData.status()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(firstData.pendingReason()).isEqualTo(PaymentPendingReason.TIMEOUT_UNKNOWN),
                () -> assertThat(lookupResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(lookupData.status()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(lookupData.pendingReason()).isNull(),
                () -> assertThat(lookupData.reason()).isEqualTo("PG 거래가 확인되지 않아 결제를 실패 처리했습니다."),
                () -> assertThat(paymentGateway.requestCount()).isEqualTo(1),
                () -> assertThat(paymentGateway.getByOrderCount()).isEqualTo(1),
                () -> assertThat(paymentJpaRepository.count()).isEqualTo(1)
            );
        }

        @DisplayName("PG 결제 생성 circuit breaker가 OPEN이면 결제 row를 만들지 않고 에러를 반환한다.")
        @Test
        void returnsServiceUnavailable_withoutCreatingPayment_whenPaymentRequestCircuitBreakerIsOpen() {
            // arrange
            signup("user1234", "abc123!?");
            OrderJpaEntity order = saveOrder("user1234", 5_000L);
            paymentGateway.setRequestAvailable(false);
            PaymentDto.RequestPayment.V1.Request request = new PaymentDto.RequestPayment.V1.Request(
                order.getId(),
                com.loopers.domain.payment.PaymentCardType.SAMSUNG,
                "1234-5678-9814-1451"
            );

            // act
            ResponseEntity<ApiResponse<PaymentDto.RequestPayment.V1.Response>> response =
                requestPayment("user1234", "abc123!?", request);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE),
                () -> assertThat(response.getBody().data()).isNull(),
                () -> assertThat(response.getBody().meta().message()).isEqualTo("PG 결제 요청이 일시적으로 차단되었습니다. 잠시 후 다시 시도해주세요."),
                () -> assertThat(paymentGateway.requestCount()).isZero(),
                () -> assertThat(paymentJpaRepository.count()).isZero()
            );
        }
    }

    @DisplayName("POST /api/v1/payments/callback")
    @Nested
    class Callback {

        @DisplayName("PG 성공 콜백을 받으면 PENDING 결제를 PAID로 전이한다.")
        @Test
        void marksPaymentPaid_whenSuccessCallbackIsReceived() {
            // arrange
            signup("user1234", "abc123!?");
            OrderJpaEntity order = saveOrder("user1234", 5_000L);
            requestPayment(order.getId());
            PaymentDto.Callback.V1.Request callback = new PaymentDto.Callback.V1.Request(
                "20260625:TR:test",
                String.valueOf(order.getId()),
                com.loopers.domain.payment.PaymentCardType.SAMSUNG,
                "1234-5678-9814-1451",
                5_000L,
                com.loopers.domain.payment.PaymentGatewayStatus.SUCCESS,
                "정상 승인되었습니다."
            );

            // act
            ResponseEntity<ApiResponse<PaymentDto.RequestPayment.V1.Response>> response =
                testRestTemplate.exchange(
                    ENDPOINT_PAYMENTS + "/callback",
                    HttpMethod.POST,
                    new HttpEntity<>(callback),
                    paymentResponseType()
                );

            // assert
            PaymentDto.RequestPayment.V1.Response data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.status()).isEqualTo(PaymentStatus.PAID),
                () -> assertThat(data.pendingReason()).isNull(),
                () -> assertThat(data.transactionKey()).isEqualTo("20260625:TR:test")
            );
        }
    }

    private OrderJpaEntity saveOrder(String userLoginId, Long amount) {
        Order order = new Order(userLoginId, List.of(new OrderLine(1L, "테스트 상품", amount, 1)));
        return orderJpaRepository.save(OrderJpaEntity.from(order));
    }

    private void requestPayment(Long orderId) {
        PaymentDto.RequestPayment.V1.Request request = new PaymentDto.RequestPayment.V1.Request(
            orderId,
            com.loopers.domain.payment.PaymentCardType.SAMSUNG,
            "1234-5678-9814-1451"
        );
        requestPayment("user1234", "abc123!?", request);
    }

    private ResponseEntity<ApiResponse<PaymentDto.RequestPayment.V1.Response>> requestPayment(
        String loginId,
        String password,
        PaymentDto.RequestPayment.V1.Request request
    ) {
        return testRestTemplate.exchange(
            ENDPOINT_PAYMENTS,
            HttpMethod.POST,
            new HttpEntity<>(request, authHeaders(loginId, password)),
            paymentResponseType()
        );
    }

    private void signup(String loginId, String password) {
        UserDto.Register.V1.Request request = new UserDto.Register.V1.Request(
            loginId,
            password,
            "홍길동",
            LocalDate.of(1990, 1, 15),
            loginId + "@example.com"
        );
        testRestTemplate.postForEntity(ENDPOINT_SIGNUP, request, String.class);
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    private ParameterizedTypeReference<ApiResponse<PaymentDto.RequestPayment.V1.Response>> paymentResponseType() {
        return new ParameterizedTypeReference<>() {};
    }

    private List<ResponseEntity<ApiResponse<PaymentDto.RequestPayment.V1.Response>>> requestPaymentConcurrently(
        int threadCount,
        String loginId,
        String password,
        PaymentDto.RequestPayment.V1.Request request
    ) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<ResponseEntity<ApiResponse<PaymentDto.RequestPayment.V1.Response>>> responses =
            Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int index = 0; index < threadCount; index++) {
                executorService.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        responses.add(requestPayment(loginId, password, request));
                    } catch (Throwable throwable) {
                        failures.add(throwable);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            assertThat(doneLatch.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(failures).isEmpty();

            return responses;
        } finally {
            executorService.shutdownNow();
        }
    }

    @TestConfiguration
    static class FakePaymentGatewayConfig {

        @Bean
        @Primary
        CountingPaymentGateway paymentGateway() {
            return new CountingPaymentGateway();
        }
    }

    static class CountingPaymentGateway implements PaymentGateway {

        private final AtomicInteger requestCount = new AtomicInteger();
        private final AtomicInteger getByOrderCount = new AtomicInteger();
        private PaymentGatewayResult requestResult = PaymentGatewayResult.pending("20260625:TR:test", null);
        private PaymentGatewayResult lookupResult = PaymentGatewayResult.pending("20260625:TR:test", null);
        private long requestDelayMillis = 0L;
        private boolean requestAvailable = true;

        @Override
        public PaymentGatewayResult request(PaymentGatewayCommand command) {
            requestCount.incrementAndGet();
            sleepRequestDelay();
            return requestResult;
        }

        @Override
        public PaymentGatewayResult getByOrder(String userLoginId, Long orderId) {
            getByOrderCount.incrementAndGet();
            return lookupResult;
        }

        void setRequestResult(PaymentGatewayResult requestResult) {
            this.requestResult = requestResult;
        }

        void setLookupResult(PaymentGatewayResult lookupResult) {
            this.lookupResult = lookupResult;
        }

        void setRequestDelayMillis(long requestDelayMillis) {
            this.requestDelayMillis = requestDelayMillis;
        }

        void setRequestAvailable(boolean requestAvailable) {
            this.requestAvailable = requestAvailable;
        }

        @Override
        public boolean isRequestAvailable() {
            return requestAvailable;
        }

        int requestCount() {
            return requestCount.get();
        }

        int getByOrderCount() {
            return getByOrderCount.get();
        }

        void reset() {
            requestCount.set(0);
            getByOrderCount.set(0);
            requestResult = PaymentGatewayResult.pending("20260625:TR:test", null);
            lookupResult = PaymentGatewayResult.pending("20260625:TR:test", null);
            requestDelayMillis = 0L;
            requestAvailable = true;
        }

        private void sleepRequestDelay() {
            if (requestDelayMillis <= 0) {
                return;
            }
            try {
                Thread.sleep(requestDelayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
    }
}
