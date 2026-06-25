package com.loopers.interfaces.api.payment;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.payment.PaymentApplicationService;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.user.UserApplicationService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgTransactionResponse;
import com.loopers.domain.payment.PgTransactionStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String ENDPOINT = "/api/v1/payments";

    @Autowired TestRestTemplate testRestTemplate;
    @Autowired UserApplicationService userApplicationService;
    @Autowired BrandApplicationService brandApplicationService;
    @Autowired ProductApplicationService productApplicationService;
    @Autowired OrderApplicationService orderApplicationService;
    @Autowired PaymentApplicationService paymentApplicationService;
    @Autowired DatabaseCleanUp databaseCleanUp;
    @MockBean PgClient pgClient;

    private String userId;
    private String orderId;
    private final String loginId = "testuser";
    private final String loginPw = "Password1!";

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @BeforeEach
    void setUp() {
        var user = userApplicationService.signup(loginId, loginPw, "홍길동",
            LocalDate.of(1990, 1, 1), "test@test.com");
        userId = user.id();

        var brand = brandApplicationService.createBrand("나이키", "스포츠 브랜드");
        var product = productApplicationService.createProduct(brand.id(), "에어맥스", "상품 설명", 100_000L, 10);
        var order = orderApplicationService.createOrder(userId, List.of(new OrderItemCommand(product.id(), 1)), null);
        orderId = order.orderId();
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, loginPw);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    class Pay {

        @DisplayName("PG 콜백이 10초 내 도착하면 SUCCESS를 반환한다.")
        @Test
        void pay_returnsSuccess_whenCallbackArrives() throws Exception {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-E2E-001", PgTransactionStatus.PENDING, null));
            when(pgClient.getTransaction(eq("TX-E2E-001"), any()))
                .thenReturn(new PgTransactionResponse("TX-E2E-001", PgTransactionStatus.SUCCESS, null));

            var executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
                Thread.sleep(100);
                HttpHeaders cbHeaders = new HttpHeaders();
                cbHeaders.setContentType(MediaType.APPLICATION_JSON);
                testRestTemplate.postForEntity(
                    ENDPOINT + "/callback",
                    new HttpEntity<>("""
                        {"transactionKey":"TX-E2E-001","orderId":"%s","cardType":"SAMSUNG",
                        "cardNo":"1234-5678-9814-1451","amount":10000,"status":"SUCCESS","reason":null}
                    """.formatted(orderId), cbHeaders),
                    Void.class
                );
                return null;
            });

            var response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(
                    "{\"orderId\":\"" + orderId + "\",\"cardType\":\"SAMSUNG\",\"cardNo\":\"1234-5678-9814-1451\"}",
                    userHeaders()
                ),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.Response>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().status()).isEqualTo("SUCCESS");
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @DisplayName("존재하지 않는 주문으로 결제 요청 시 404를 반환한다.")
        @Test
        void pay_returns404_whenOrderNotFound() {
            var response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(
                    "{\"orderId\":99999,\"cardType\":\"SAMSUNG\",\"cardNo\":\"1234-5678-9814-1451\"}",
                    userHeaders()
                ),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.Response>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("다른 유저의 주문으로 결제 요청 시 404를 반환한다.")
        @Test
        void pay_returns404_whenOrderOwnedByAnotherUser() {
            userApplicationService.signup("otheruser", "Password1!", "이몽룡",
                LocalDate.of(1991, 2, 2), "other@test.com");
            HttpHeaders otherHeaders = new HttpHeaders();
            otherHeaders.set(HEADER_LOGIN_ID, "otheruser");
            otherHeaders.set(HEADER_LOGIN_PW, "Password1!");
            otherHeaders.setContentType(MediaType.APPLICATION_JSON);

            var response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(
                    "{\"orderId\":\"" + orderId + "\",\"cardType\":\"SAMSUNG\",\"cardNo\":\"1234-5678-9814-1451\"}",
                    otherHeaders
                ),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.Response>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("확정 이후 PG가 동일 SUCCESS 콜백을 재전송(at-least-once)해도 200으로 멱등 처리되고 상태가 유지된다.")
        @Test
        void pay_handlesDuplicateSuccessCallback_idempotently() throws Exception {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-DUP", PgTransactionStatus.PENDING, null));
            when(pgClient.getTransaction(eq("TX-DUP"), any()))
                .thenReturn(new PgTransactionResponse("TX-DUP", PgTransactionStatus.SUCCESS, null));

            // timeout(2s) 후 1차 Poll SUCCESS 확정
            var response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(
                    "{\"orderId\":\"" + orderId + "\",\"cardType\":\"SAMSUNG\",\"cardNo\":\"1234-5678-9814-1451\"}",
                    userHeaders()
                ),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.Response>>() {}
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().status()).isEqualTo("SUCCESS");

            // 확정 이후 동일 SUCCESS 콜백 재전송 → 200, 상태 유지
            HttpHeaders cbHeaders = new HttpHeaders();
            cbHeaders.setContentType(MediaType.APPLICATION_JSON);
            var callbackResponse = testRestTemplate.postForEntity(
                ENDPOINT + "/callback",
                new HttpEntity<>("""
                    {"transactionKey":"TX-DUP","orderId":"%s","cardType":"SAMSUNG",
                    "cardNo":"1234-5678-9814-1451","amount":10000,"status":"SUCCESS","reason":null}
                """.formatted(orderId), cbHeaders),
                Void.class
            );
            assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            String paymentId = paymentApplicationService.getPaymentByTransactionKey("TX-DUP").paymentId();
            assertThat(paymentApplicationService.getPayment(userId, paymentId).status())
                .isEqualTo(com.loopers.domain.payment.PaymentStatus.SUCCESS);
        }
    }

    @DisplayName("POST /api/v1/payments/callback")
    @Nested
    class Callback {

        @DisplayName("콜백 엔드포인트는 인증 없이 호출해도 200을 반환한다.")
        @Test
        void callback_returns200_withoutAuth() {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-NOAUTH", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            HttpHeaders noAuth = new HttpHeaders();
            noAuth.setContentType(MediaType.APPLICATION_JSON);

            var response = testRestTemplate.postForEntity(
                ENDPOINT + "/callback",
                new HttpEntity<>("""
                    {"transactionKey":"TX-NOAUTH","orderId":"%s","cardType":"SAMSUNG",
                    "cardNo":"1234-5678-9814-1451","amount":10000,"status":"SUCCESS","reason":null}
                """.formatted(orderId), noAuth),
                Void.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("GET /api/v1/payments/{paymentId}")
    @Nested
    class GetPayment {

        @DisplayName("DB가 PENDING이면 PG 조회 후 SUCCESS를 반환한다.")
        @Test
        void getPayment_returnSuccess_afterPgPoll() {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-E2E-002", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");

            when(pgClient.getTransaction(eq("TX-E2E-002"), any()))
                .thenReturn(new PgTransactionResponse("TX-E2E-002", PgTransactionStatus.SUCCESS, null));

            String paymentId = paymentApplicationService.getPaymentByTransactionKey("TX-E2E-002").paymentId();

            var response = testRestTemplate.exchange(
                ENDPOINT + "/" + paymentId,
                HttpMethod.GET,
                new HttpEntity<>(userHeaders()),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.Response>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().status()).isEqualTo("SUCCESS");
        }

        @DisplayName("다른 유저가 타인의 결제를 조회하면 404를 반환한다.")
        @Test
        void getPayment_returns404_whenNotOwner() {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-E2E-OWNER", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            String paymentId = paymentApplicationService.getPaymentByTransactionKey("TX-E2E-OWNER").paymentId();

            userApplicationService.signup("otheruser", "Password1!", "이몽룡",
                LocalDate.of(1991, 2, 2), "other@test.com");
            HttpHeaders otherHeaders = new HttpHeaders();
            otherHeaders.set(HEADER_LOGIN_ID, "otheruser");
            otherHeaders.set(HEADER_LOGIN_PW, "Password1!");

            var response = testRestTemplate.exchange(
                ENDPOINT + "/" + paymentId,
                HttpMethod.GET,
                new HttpEntity<>(otherHeaders),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.Response>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("DB가 PENDING이고 PG 조회가 PG_QUERY_ERROR로 실패하면 HTTP 500을 반환한다.")
        @Test
        void getPayment_returns500_whenPgQueryFails() {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-E2E-ERR", PgTransactionStatus.PENDING, null));
            paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
            String paymentId = paymentApplicationService.getPaymentByTransactionKey("TX-E2E-ERR").paymentId();

            when(pgClient.getTransaction(eq("TX-E2E-ERR"), any()))
                .thenThrow(new com.loopers.support.error.CoreException(
                    com.loopers.support.error.ErrorType.PG_QUERY_ERROR, "PG 조회 실패"));

            var response = testRestTemplate.exchange(
                ENDPOINT + "/" + paymentId,
                HttpMethod.GET,
                new HttpEntity<>(userHeaders()),
                new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.Response>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DisplayName("동시성")
    @Nested
    class Concurrency {

        @DisplayName("동일 orderId에 initiate를 동시 2회 호출하면 하나만 성공하고 나머지는 CONFLICT가 된다.")
        @Test
        void initiate_allowsOnlyOne_whenCalledConcurrently() throws Exception {
            when(pgClient.requestPayment(any(), any()))
                .thenReturn(new PgTransactionResponse("TX-CONCURRENT", PgTransactionStatus.PENDING, null));

            var executor = Executors.newFixedThreadPool(2);
            var latch = new java.util.concurrent.CountDownLatch(1);
            var successCount = new java.util.concurrent.atomic.AtomicInteger();
            var conflictCount = new java.util.concurrent.atomic.AtomicInteger();

            Runnable task = () -> {
                try {
                    latch.await();
                    paymentApplicationService.initiate(userId, orderId, CardType.SAMSUNG, "1234-5678-9814-1451");
                    successCount.incrementAndGet();
                } catch (com.loopers.support.error.CoreException e) {
                    if (e.getErrorType() == com.loopers.support.error.ErrorType.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            };
            var f1 = executor.submit(task);
            var f2 = executor.submit(task);
            latch.countDown();
            f1.get(10, java.util.concurrent.TimeUnit.SECONDS);
            f2.get(10, java.util.concurrent.TimeUnit.SECONDS);
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(conflictCount.get()).isEqualTo(1);
        }
    }
}
