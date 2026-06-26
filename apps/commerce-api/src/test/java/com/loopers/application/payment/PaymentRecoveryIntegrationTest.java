package com.loopers.application.payment;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = "pg.base-url=http://localhost:18086")
class PaymentRecoveryIntegrationTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig().port(18086))
        .build();

    @Autowired private PaymentFacade paymentFacade;
    @Autowired private PaymentService paymentService;
    @Autowired private UserJpaRepository userJpaRepository;
    @Autowired private OrderJpaRepository orderJpaRepository;
    @Autowired private PaymentJpaRepository paymentJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        wiremock.resetAll();
    }

    private OrderModel order;

    /** PENDING 주문 + PENDING 결제(transactionKey 연결됨)를 만든다. */
    private PaymentModel givenPendingPaymentWithKey(String transactionKey) {
        UserModel user = userJpaRepository.save(new UserModel("hajin01", "pw1"));
        order = orderJpaRepository.save(new OrderModel(user.getId(), 5000L, 0L, 5000L, null));
        PaymentModel payment = paymentService.create(user.getId(), order.getId(), CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);
        return paymentService.linkTransactionKey(payment.getId(), transactionKey);
    }

    private void stubPgGet(String status) {
        wiremock.stubFor(get(urlPathMatching("/api/v1/payments/.*")).willReturn(okJson(
            "{\"meta\":{\"result\":\"SUCCESS\"},\"data\":{\"transactionKey\":\"txkey-1\",\"orderId\":\"1\",\"cardType\":\"SAMSUNG\",\"cardNo\":\"1234-5678-9814-1451\",\"amount\":5000,\"status\":\"" + status + "\",\"reason\":\"r\"}}"
        )));
    }

    @DisplayName("PG 조회 결과가 SUCCESS면, 결제는 SUCCESS·주문은 PAID로 정정된다.")
    @Test
    void reconcile_confirmsOrder_whenPgSuccess() {
        PaymentModel payment = givenPendingPaymentWithKey("txkey-1");
        stubPgGet("SUCCESS");

        paymentFacade.reconcile("txkey-1");

        assertAll(
            () -> assertThat(paymentJpaRepository.findByTransactionKey("txkey-1").orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCESS),
            () -> assertThat(orderJpaRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID)
        );
    }

    @DisplayName("PG가 아직 PENDING이면, 아무것도 바꾸지 않는다 (처리 미완).")
    @Test
    void reconcile_noChange_whenPgStillPending() {
        givenPendingPaymentWithKey("txkey-1");
        stubPgGet("PENDING");

        paymentFacade.reconcile("txkey-1");

        assertAll(
            () -> assertThat(paymentJpaRepository.findByTransactionKey("txkey-1").orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING),
            () -> assertThat(orderJpaRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING)
        );
    }

    @DisplayName("reconcileAll은 키를 가진 PENDING 결제건들을 PG 조회로 일괄 정정한다.")
    @Test
    void reconcileAll_reconcilesPendingWithKey() {
        givenPendingPaymentWithKey("txkey-1");
        stubPgGet("SUCCESS");

        paymentFacade.reconcileAll();

        assertThat(paymentJpaRepository.findByTransactionKey("txkey-1").orElseThrow().getStatus())
            .isEqualTo(PaymentStatus.SUCCESS);
    }
}
