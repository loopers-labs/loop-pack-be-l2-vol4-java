package com.loopers.application.payment;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.domain.user.UserModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 타임아웃이 아니라 Facade 오케스트레이션을 검증한다. WireMock을 고정 포트로 띄우고
// pg.base-url을 정적 프로퍼티로 고정한다. (dynamicPort + @DynamicPropertySource는 Spring
// 컨텍스트 캐싱과 얽혀 포트 불일치(Connection refused)를 유발 — 고정 포트로 회피)
@SpringBootTest(properties = {
    "pg.base-url=http://localhost:18083"
})
class PaymentFacadeIntegrationTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig().port(18083))
        .build();

    @Autowired
    private PaymentFacade paymentFacade;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        wiremock.resetAll();
    }

    private static final String LOGIN_ID = "hajin01";
    private static final String LOGIN_PW = "pw1";
    private static final String CARD_NO = "1234-5678-9814-1451";
    private static final String TX_KEY = "20260626:TR:abc123";

    private UserModel givenUser() {
        return userJpaRepository.save(new UserModel(LOGIN_ID, LOGIN_PW));
    }

    private OrderModel givenPendingOrder(Long userId, long finalPrice) {
        return orderJpaRepository.save(new OrderModel(userId, finalPrice, 0L, finalPrice, null));
    }

    private void stubPgSuccess() {
        wiremock.stubFor(post(urlEqualTo("/api/v1/payments")).willReturn(okJson(
            "{\"meta\":{\"result\":\"SUCCESS\"},\"data\":{\"transactionKey\":\"" + TX_KEY + "\",\"status\":\"PENDING\",\"reason\":null}}"
        )));
    }

    @DisplayName("결제를 요청할 때, ")
    @Nested
    class RequestPayment {

        @DisplayName("정상 흐름이면, 결제건이 PENDING+transactionKey로 생성되고 주문은 아직 PENDING이다.")
        @Test
        void createsPendingPayment_andKeepsOrderPending() {
            UserModel user = givenUser();
            OrderModel order = givenPendingOrder(user.getId(), 5000L);
            stubPgSuccess();

            PaymentInfo info = paymentFacade.requestPayment(LOGIN_ID, LOGIN_PW, order.getId(), CardType.SAMSUNG, CARD_NO);

            PaymentModel saved = paymentJpaRepository.findByTransactionKey(TX_KEY).orElseThrow();
            assertAll(
                () -> assertThat(info.transactionKey()).isEqualTo(TX_KEY),
                () -> assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(saved.getAmount()).isEqualTo(5000L),
                () -> assertThat(saved.getOrderId()).isEqualTo(order.getId()),
                () -> assertThat(orderJpaRepository.findById(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PENDING)
            );
        }

        @DisplayName("본인 주문이 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenOrderNotOwned() {
            UserModel user = givenUser();
            OrderModel othersOrder = givenPendingOrder(999L, 5000L);

            CoreException ex = assertThrows(CoreException.class, () ->
                paymentFacade.requestPayment(LOGIN_ID, LOGIN_PW, othersOrder.getId(), CardType.SAMSUNG, CARD_NO));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("PG 콜백을 처리할 때, ")
    @Nested
    class HandleCallback {

        @DisplayName("SUCCESS 콜백이면, 결제는 SUCCESS, 주문은 PAID가 된다.")
        @Test
        void marksPaymentSuccess_andConfirmsOrder() {
            UserModel user = givenUser();
            OrderModel order = givenPendingOrder(user.getId(), 5000L);
            stubPgSuccess();
            paymentFacade.requestPayment(LOGIN_ID, LOGIN_PW, order.getId(), CardType.SAMSUNG, CARD_NO);

            paymentFacade.handleCallback(TX_KEY, PaymentStatus.SUCCESS, "정상 승인되었습니다.");

            assertAll(
                () -> assertThat(paymentJpaRepository.findByTransactionKey(TX_KEY).orElseThrow().getStatus())
                    .isEqualTo(PaymentStatus.SUCCESS),
                () -> assertThat(orderJpaRepository.findById(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PAID)
            );
        }

        @DisplayName("같은 SUCCESS 콜백이 두 번 와도, 멱등하게 주문은 PAID를 유지한다.")
        @Test
        void isIdempotent_onDuplicateCallback() {
            UserModel user = givenUser();
            OrderModel order = givenPendingOrder(user.getId(), 5000L);
            stubPgSuccess();
            paymentFacade.requestPayment(LOGIN_ID, LOGIN_PW, order.getId(), CardType.SAMSUNG, CARD_NO);
            paymentFacade.handleCallback(TX_KEY, PaymentStatus.SUCCESS, "정상 승인되었습니다.");

            paymentFacade.handleCallback(TX_KEY, PaymentStatus.SUCCESS, "중복 콜백");

            assertThat(orderJpaRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
        }
    }
}
