package com.loopers.interfaces.api.payment;

import com.loopers.domain.catalog.brand.Brand;
import com.loopers.domain.catalog.brand.BrandRepository;
import com.loopers.domain.catalog.product.Product;
import com.loopers.domain.catalog.product.ProductRepository;
import com.loopers.domain.payment.gateway.PaymentGateway;
import com.loopers.domain.payment.gateway.PaymentGatewayCommand;
import com.loopers.domain.payment.gateway.PaymentGatewayResult;
import com.loopers.domain.payment.payment.Payment;
import com.loopers.domain.payment.payment.PaymentRepository;
import com.loopers.domain.payment.payment.PaymentStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.ordering.OrderDto;
import com.loopers.interfaces.api.support.HeaderValidator;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentApiE2ETest {

    private final org.springframework.boot.test.web.client.TestRestTemplate testRestTemplate;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final FakePaymentGateway fakePaymentGateway;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    PaymentApiE2ETest(
        org.springframework.boot.test.web.client.TestRestTemplate testRestTemplate,
        BrandRepository brandRepository,
        ProductRepository productRepository,
        PaymentRepository paymentRepository,
        FakePaymentGateway fakePaymentGateway,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.paymentRepository = paymentRepository;
        this.fakePaymentGateway = fakePaymentGateway;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        fakePaymentGateway.reset();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/payments 는 PG 결제 요청을 보내고 내부 결제를 PROCESSING 상태로 반영한다.")
    @Test
    void requestsPgPaymentAndMarksProcessing() {
        // arrange
        Product product = saveProduct("상품", 1_000L, 10);
        Long orderId = placeOrder(product.getId());

        // act
        ParameterizedTypeReference<ApiResponse<PaymentDto.PaymentResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<PaymentDto.PaymentResponse>> response = testRestTemplate.exchange(
            "/api/v1/payments",
            HttpMethod.POST,
            new HttpEntity<>(
                new PaymentDto.PaymentRequest(orderId, PaymentDto.CardType.SAMSUNG, "1234-5678-9814-1451"),
                userHeaders("user1")
            ),
            responseType
        );

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();

        // assert
        assertAll(
            () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
            () -> assertThat(response.getBody().data().paymentStatus()).isEqualTo(PaymentStatus.PROCESSING),
            () -> assertThat(response.getBody().data().transactionKey()).isEqualTo("20250816:TR:9577c5"),
            () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING),
            () -> assertThat(payment.getTransactionKey()).isEqualTo("20250816:TR:9577c5"),
            () -> assertThat(fakePaymentGateway.command.orderId()).isEqualTo(String.format("%06d", orderId)),
            () -> assertThat(fakePaymentGateway.command.amount()).isEqualTo(1_000L)
        );
    }

    private Product saveProduct(String name, Long price, Integer stockQuantity) {
        Brand brand = brandRepository.save(new Brand("Loopers", "테스트 브랜드"));
        return productRepository.save(new Product(brand.getId(), name, "설명", price, stockQuantity));
    }

    private Long placeOrder(Long productId) {
        ParameterizedTypeReference<ApiResponse<OrderDto.OrderCreateResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<OrderDto.OrderCreateResponse>> response = testRestTemplate.exchange(
            "/api/v1/orders",
            HttpMethod.POST,
            new HttpEntity<>(
                new OrderDto.OrderCreateRequest(List.of(new OrderDto.OrderCreateItemRequest(productId, 1))),
                userHeaders("user1")
            ),
            responseType
        );
        return response.getBody().data().orderId();
    }

    private HttpHeaders userHeaders(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HeaderValidator.LOGIN_ID, userId);
        headers.add(HeaderValidator.LOGIN_PW, "password");
        return headers;
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakePaymentGateway fakePaymentGateway() {
            return new FakePaymentGateway();
        }
    }

    static class FakePaymentGateway implements PaymentGateway {
        private PaymentGatewayCommand.Request command;

        @Override
        public PaymentGatewayResult requestPayment(PaymentGatewayCommand.Request command) {
            this.command = command;
            return PaymentGatewayResult.pending("20250816:TR:9577c5", command.orderId());
        }

        private void reset() {
            command = null;
        }
    }
}
