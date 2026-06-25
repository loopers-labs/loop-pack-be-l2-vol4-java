package com.loopers.interfaces.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.PgRequestResult;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStockService;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.auth.AuthenticatedUserArgumentResolver;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentV1ApiE2ETest {

    private static final String PAY_ENDPOINT = "/api/v1/payments";
    private static final String CALLBACK_ENDPOINT = "/api/v1/payments/callback";
    private static final String LOGIN_ID = "minbo";
    private static final String PASSWORD = "Test1234!";
    private static final String CARD_NO = "1234-5678-9814-1451";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductStockService productStockService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockBean
    private PaymentGateway paymentGateway;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long signUp() {
        userService.createUser(LOGIN_ID, PASSWORD, "민보", LocalDate.of(1991, 8, 21), "test@example.com");
        return userService.getMyInfo(LOGIN_ID).getId();
    }

    private OrderModel placePendingOrder(Long userId, Long price, int quantity) {
        ProductModel product = productService.createProduct(1L, "티셔츠", "설명", price, 100);
        return orderService.createPendingOrder(userId, List.of(OrderLine.of(product.getId(), quantity)), null);
    }

    private MvcResult requestPayment(Long orderId) throws Exception {
        String body = objectMapper.writeValueAsString(
                new PayBody(orderId, "SAMSUNG", CARD_NO));
        return mockMvc.perform(post(PAY_ENDPOINT)
                        .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_ID, LOGIN_ID)
                        .header(AuthenticatedUserArgumentResolver.HEADER_LOGIN_PW, PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private MvcResult sendCallback(String transactionKey, Long orderId, Long amount, String status, String reason) throws Exception {
        String body = objectMapper.writeValueAsString(new CallbackBody(
                transactionKey, String.format("%06d", orderId), "SAMSUNG", CARD_NO, amount, status, reason));
        return mockMvc.perform(post(CALLBACK_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
    }

    private record PayBody(Long orderId, String cardType, String cardNo) {
    }

    private record CallbackBody(String transactionKey, String orderId, String cardType, String cardNo,
                                Long amount, String status, String reason) {
    }

    @DisplayName("POST /api/v1/payments — 결제 접수")
    @Nested
    class Pay {

        @DisplayName("접수되면, 결제가 PENDING 으로 생성되고 금액은 주문 기준으로 산출된다.")
        @Test
        void acceptsAsPending_withOrderAmount() throws Exception {
            Long userId = signUp();
            OrderModel order = placePendingOrder(userId, 5000L, 2);
            given(paymentGateway.requestPayment(any())).willReturn(PgRequestResult.accepted("tx-1"));

            MvcResult result = requestPayment(order.getId());

            PaymentModel payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(payment.getTransactionKey()).isEqualTo("tx-1"),
                    () -> assertThat(payment.getAmount()).isEqualTo(10000L),
                    () -> assertThat(payment.isPgRequestAttempted()).isTrue()
            );
        }

        @DisplayName("이미 결제가 접수된 주문에 다시 요청하면, 409 예외 발생.")
        @Test
        void rejectsDuplicate() throws Exception {
            Long userId = signUp();
            OrderModel order = placePendingOrder(userId, 5000L, 1);
            given(paymentGateway.requestPayment(any())).willReturn(PgRequestResult.accepted("tx-1"));
            requestPayment(order.getId());

            MvcResult result = requestPayment(order.getId());

            assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        }

        @DisplayName("타임아웃이면, PENDING 유지하고 transactionKey 없이 시도 플래그만 켜진다.")
        @Test
        void timeout_keepsPendingWithoutKey() throws Exception {
            Long userId = signUp();
            OrderModel order = placePendingOrder(userId, 5000L, 1);
            given(paymentGateway.requestPayment(any())).willReturn(PgRequestResult.timeout());

            requestPayment(order.getId());

            PaymentModel payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING),
                    () -> assertThat(payment.getTransactionKey()).isNull(),
                    () -> assertThat(payment.isPgRequestAttempted()).isTrue()
            );
        }
    }

    @DisplayName("POST /api/v1/payments/callback — 결과 반영")
    @Nested
    class Callback {

        private OrderModel paid(Long userId, Long price, int qty, String txKey) throws Exception {
            OrderModel order = placePendingOrder(userId, price, qty);
            given(paymentGateway.requestPayment(any())).willReturn(PgRequestResult.accepted(txKey));
            requestPayment(order.getId());
            return order;
        }

        @DisplayName("SUCCESS 콜백이면, 주문이 PAID 로 확정된다.")
        @Test
        void success_confirmsOrder() throws Exception {
            Long userId = signUp();
            OrderModel order = paid(userId, 5000L, 2, "tx-ok");

            sendCallback("tx-ok", order.getId(), 10000L, "SUCCESS", null);

            assertThat(orderService.getOrder(order.getId()).getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @DisplayName("FAILED 콜백이면, 주문이 FAILED 로 바뀌고 재고가 복구된다.")
        @Test
        void failed_compensatesOrder() throws Exception {
            Long userId = signUp();
            OrderModel order = paid(userId, 5000L, 3, "tx-fail");
            Long productId = order.getOrderItems().get(0).getProductId();
            long stockBefore = productStockService.getStock(productId).getStock().value();

            sendCallback("tx-fail", order.getId(), 15000L, "FAILED", "한도 초과");

            assertAll(
                    () -> assertThat(orderService.getOrder(order.getId()).getStatus()).isEqualTo(OrderStatus.FAILED),
                    () -> assertThat(productStockService.getStock(productId).getStock().value()).isEqualTo(stockBefore + 3)
            );
        }

        @DisplayName("같은 SUCCESS 콜백이 중복으로 와도, 주문은 한 번만 확정된다(멱등).")
        @Test
        void duplicateCallback_isIdempotent() throws Exception {
            Long userId = signUp();
            OrderModel order = paid(userId, 5000L, 1, "tx-dup");

            sendCallback("tx-dup", order.getId(), 5000L, "SUCCESS", null);
            MvcResult second = sendCallback("tx-dup", order.getId(), 5000L, "SUCCESS", null);

            assertAll(
                    () -> assertThat(second.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value()),
                    () -> assertThat(orderService.getOrder(order.getId()).getStatus()).isEqualTo(OrderStatus.PAID)
            );
        }

        @DisplayName("콜백 금액이 주문 금액과 다르면, 400 예외 발생.")
        @Test
        void amountMismatch_isRejected() throws Exception {
            Long userId = signUp();
            OrderModel order = paid(userId, 5000L, 2, "tx-amt");

            MvcResult result = sendCallback("tx-amt", order.getId(), 9999L, "SUCCESS", null);

            assertAll(
                    () -> assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value()),
                    () -> assertThat(orderService.getOrder(order.getId()).getStatus()).isEqualTo(OrderStatus.PENDING)
            );
        }
    }
}