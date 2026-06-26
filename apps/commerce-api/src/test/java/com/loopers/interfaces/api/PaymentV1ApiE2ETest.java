package com.loopers.interfaces.api;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderLineCommand;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.money.Money;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    private static final String ENDPOINT_CALLBACK = "/api/v1/payments/callback";
    private static final String TX = "tx-001";
    private static final String CARD_NO = "1234-1234-1234-1234";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private OrderFacade orderFacade;
    @Autowired
    private BrandJpaRepository brandJpaRepository;
    @Autowired
    private ProductJpaRepository productJpaRepository;
    @Autowired
    private OrderJpaRepository orderJpaRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @BeforeEach
    void stubPaymentGateway() {
        given(paymentGateway.requestPayment(any()))
            .willReturn(new PaymentGateway.PaymentResult(TX, PaymentStatus.PENDING, null));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderInfo placePendingOrder() {
        Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));
        Product product = productJpaRepository.save(new Product("에어맥스", "편한 러닝화",
            new Money(BigDecimal.valueOf(1000)), new Stock(10), brand.getId()));
        return orderFacade.place(1L,
            List.of(new OrderLineCommand(product.getId(), 3)), null, CardType.SAMSUNG, CARD_NO);
    }

    @DisplayName("POST /api/v1/payments/callback")
    @Nested
    class Callback {
        @DisplayName("PG 콜백 본문(불필요 필드 포함)을 보내면, 인증 없이 수신되어 주문이 PAID 로 확정된다.")
        @Test
        void confirmsOrder_whenCallbackReceived() {
            // arrange — PENDING 주문/결제 생성 (transactionKey = TX)
            OrderInfo info = placePendingOrder();
            // 시뮬레이터가 실제로 보내는 본문: 우리가 안 쓰는 필드(orderId/cardType/cardNo/amount)도 포함된다
            Map<String, Object> body = Map.of(
                "transactionKey", TX,
                "orderId", "000001",
                "cardType", "SAMSUNG",
                "cardNo", CARD_NO,
                "amount", 3000,
                "status", "SUCCESS"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_CALLBACK, HttpMethod.POST, new HttpEntity<>(body), responseType);

            // assert
            Order order = orderJpaRepository.findById(info.id()).orElseThrow();
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID)
            );
        }
    }
}
