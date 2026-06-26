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

import java.time.ZonedDateTime;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = "pg.base-url=http://localhost:18087")
class PaymentOrphanRecoveryIntegrationTest {

    @RegisterExtension
    static WireMockExtension wiremock = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig().port(18087))
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
    private PaymentModel orphan;

    /** transactionKey가 끝내 안 붙은 PENDING(미아 결제)을 만든다. (create만, link 없음) */
    private void givenOrphanPayment() {
        UserModel user = userJpaRepository.save(new UserModel("hajin01", "pw1"));
        order = orderJpaRepository.save(new OrderModel(user.getId(), 5000L, 0L, 5000L, null));
        orphan = paymentService.create(user.getId(), order.getId(), CardType.SAMSUNG, "1234-5678-9814-1451", 5000L);
    }

    private ZonedDateTime cutoffIncludingNow() {
        return ZonedDateTime.now().plusMinutes(1);
    }

    @DisplayName("PG에 SUCCESS 결제건이 있으면, 미아 결제에 키를 입양하고 SUCCESS·주문 PAID로 정정한다.")
    @Test
    void adoptsTransaction_whenPgHasIt() {
        givenOrphanPayment();
        wiremock.stubFor(get(urlPathEqualTo("/api/v1/payments")).withQueryParam("orderId", equalTo(String.valueOf(order.getId())))
            .willReturn(okJson(
                "{\"meta\":{\"result\":\"SUCCESS\"},\"data\":{\"orderId\":\"" + order.getId() + "\",\"transactions\":[{\"transactionKey\":\"pgkey-1\",\"status\":\"SUCCESS\",\"reason\":\"정상 승인되었습니다.\"}]}}"
            )));

        paymentFacade.recoverOrphans(cutoffIncludingNow());

        assertAll(
            () -> assertThat(paymentJpaRepository.findById(orphan.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCESS),
            () -> assertThat(paymentJpaRepository.findById(orphan.getId()).orElseThrow().getTransactionKey())
                .isEqualTo("pgkey-1"),
            () -> assertThat(orderJpaRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID)
        );
    }

    @DisplayName("PG에 결제건이 없으면(404), 미아 결제를 EXPIRED로 종결한다.")
    @Test
    void expires_whenPgHasNothing() {
        givenOrphanPayment();
        wiremock.stubFor(get(urlPathEqualTo("/api/v1/payments")).withQueryParam("orderId", equalTo(String.valueOf(order.getId())))
            .willReturn(aResponse().withStatus(404)));

        paymentFacade.recoverOrphans(cutoffIncludingNow());

        assertAll(
            () -> assertThat(paymentJpaRepository.findById(orphan.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.EXPIRED),
            () -> assertThat(orderJpaRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING)
        );
    }
}
