package com.loopers.interfaces.api.payment;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.ProductStockRepository;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.enums.UserRole;
import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.UserId;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderV1Dto;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest {

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductStockRepository productStockRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final String DEFAULT_USERID = "payUser1";
    private static final String DEFAULT_PASSWORD = "Dlaxodid1!";

    private ProductStockModel savedStock;

    @BeforeEach
    void setUp() {
        userRepository.save(new UserModel(
                new UserId(DEFAULT_USERID),
                new Password(passwordEncoder.encode(DEFAULT_PASSWORD)),
                new Name("결제유저"),
                new BirthDay("1990-01-01"),
                new Email("pay@test.com"),
                UserRole.USER
        ));
        BrandModel brand = brandRepository.save(new BrandModel("테스트브랜드"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), new ProductName("테스트상품")));
        savedStock = productStockRepository.save(new ProductStockModel(product, new Price(10000L), 10));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", DEFAULT_USERID);
        headers.set("X-Loopers-LoginPw", DEFAULT_PASSWORD);
        return headers;
    }

    private String orderNumber() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return LocalDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + suffix;
    }

    private Long createOrder() {
        OrderV1Dto.OrderRequest request = new OrderV1Dto.OrderRequest(
                orderNumber(),
                List.of(new OrderV1Dto.OrderItemRequest(savedStock.getId(), 1)),
                null
        );
        ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange("/api/v1/orders", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()), type).getBody().data().id();
    }

    private Long createPayment(Long orderId) {
        PaymentV1Dto.CreateRequest request = new PaymentV1Dto.CreateRequest(orderId);
        ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange("/api/v1/payments", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders()), type).getBody().data().id();
    }

    @DisplayName("POST /api/v1/payments")
    @Nested
    class CreatePayment {

        @DisplayName("유효한 주문이면, 201 CREATED와 결제 정보를 반환한다.")
        @Test
        void returnsPayment_whenOrderIsValid() {
            Long orderId = createOrder();
            PaymentV1Dto.CreateRequest request = new PaymentV1Dto.CreateRequest(orderId);
            ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    "/api/v1/payments", HttpMethod.POST, new HttpEntity<>(request, authHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody().data().orderId()).isEqualTo(orderId);
            assertThat(response.getBody().data().amount()).isEqualTo(10000L);
            assertThat(response.getBody().data().status()).isEqualTo("결제 대기");
        }
    }

    @DisplayName("POST /api/v1/payments/{paymentId}/approve")
    @Nested
    class ApprovePayment {

        @DisplayName("대기 상태의 결제이면, 200 OK와 승인된 결제 정보를 반환한다.")
        @Test
        void approvesPayment_whenStatusIsPending() {
            Long paymentId = createPayment(createOrder());
            ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    "/api/v1/payments/" + paymentId + "/approve", HttpMethod.POST, new HttpEntity<>(null, authHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().status()).isEqualTo("결제 승인");
        }
    }

    @DisplayName("POST /api/v1/payments/{paymentId}/fail")
    @Nested
    class FailPayment {

        @DisplayName("대기 상태의 결제이면, 200 OK와 실패 처리된 결제 정보를 반환한다.")
        @Test
        void failsPayment_whenStatusIsPending() {
            Long paymentId = createPayment(createOrder());
            ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>> response = testRestTemplate.exchange(
                    "/api/v1/payments/" + paymentId + "/fail", HttpMethod.POST, new HttpEntity<>(null, authHeaders()), type);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().status()).isEqualTo("결제 실패");
        }
    }
}
