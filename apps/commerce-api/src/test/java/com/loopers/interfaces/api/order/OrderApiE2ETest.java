package com.loopers.interfaces.api.order;

import com.loopers.application.user.UserFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.admin.coupon.dto.CouponTemplateV1Response;
import com.loopers.interfaces.api.admin.coupon.dto.CreateCouponTemplateV1Request;
import com.loopers.interfaces.api.coupon.dto.IssueCouponV1Response;
import com.loopers.interfaces.api.order.dto.OrderV1Response;
import com.loopers.interfaces.api.order.dto.PlaceOrderV1Request;
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
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderApiE2ETest {

    private static final String ENDPOINT = "/api/v1/orders";
    private static final String LOGIN_ID = "loopers01";
    private static final String PASSWORD = "Pass1234!";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserFacade userFacade;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long product1Id;
    private Long product2Id;

    @BeforeEach
    void setUp() {
        userFacade.signUp(LOGIN_ID, PASSWORD, "홍길동", LocalDate.of(1990, 1, 15), "test@loopers.com");

        BrandModel brand = brandRepository.save(new BrandModel("Loopers", "감성"));
        product1Id = productRepository.save(new ProductModel(brand.getId(), "후드", "포근함", 50_000L)).getId();
        product2Id = productRepository.save(new ProductModel(brand.getId(), "맨투맨", "심플", 30_000L)).getId();
        stockRepository.save(new StockModel(product1Id, 10));
        stockRepository.save(new StockModel(product2Id, 5));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", PASSWORD);
        return headers;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    @DisplayName("POST /api/v1/orders — 주문 생성 시")
    @Nested
    class PlaceOrder {

        @DisplayName("쿠폰 없이 정상 주문하면 200 OK이고 금액이 그대로 반영된다")
        @Test
        void returns200_andAmounts_whenValidOrderWithoutCoupon() {
            // given
            PlaceOrderV1Request request = new PlaceOrderV1Request(List.of(
                new PlaceOrderV1Request.OrderLineV1Request(product1Id, 2),
                new PlaceOrderV1Request.OrderLineV1Request(product2Id, 1)
            ), null);

            // when
            ResponseEntity<ApiResponse<OrderV1Response>> response = restTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, userHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getBody()).isNotNull();
            OrderV1Response data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.totalAmount()).isEqualTo(130_000L),
                () -> assertThat(data.discountAmount()).isZero(),
                () -> assertThat(data.finalAmount()).isEqualTo(130_000L),
                () -> assertThat(data.items()).hasSize(2)
            );
        }

        @DisplayName("인증 헤더가 없으면 401 UNAUTHORIZED 를 반환한다")
        @Test
        void returns401_whenAuthHeadersMissing() {
            PlaceOrderV1Request request = new PlaceOrderV1Request(List.of(
                new PlaceOrderV1Request.OrderLineV1Request(product1Id, 1)
            ), null);

            ResponseEntity<ApiResponse<OrderV1Response>> response = restTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, new HttpHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("재고가 부족하면 409 CONFLICT 를 반환한다")
        @Test
        void returns409_whenStockInsufficient() {
            PlaceOrderV1Request request = new PlaceOrderV1Request(List.of(
                new PlaceOrderV1Request.OrderLineV1Request(product2Id, 10)
            ), null);

            ResponseEntity<ApiResponse<OrderV1Response>> response = restTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, userHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("어드민이 만든 쿠폰을 발급받아 주문 전체 금액에 적용하면 할인이 반영된다")
        @Test
        void appliesCoupon_throughFullFlow() {
            // given - 어드민이 정률 10% 쿠폰 템플릿 생성
            CreateCouponTemplateV1Request createReq = new CreateCouponTemplateV1Request(
                "10% 할인", CouponType.RATE, 10L, null, LocalDateTime.now().plusDays(7));
            ResponseEntity<ApiResponse<CouponTemplateV1Response>> createRes = restTemplate.exchange(
                "/api-admin/v1/coupons", HttpMethod.POST, new HttpEntity<>(createReq, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            Long templateId = createRes.getBody().data().id();

            // 유저가 쿠폰 발급
            ResponseEntity<ApiResponse<IssueCouponV1Response>> issueRes = restTemplate.exchange(
                "/api/v1/coupons/" + templateId + "/issue", HttpMethod.POST, new HttpEntity<>(userHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            Long couponId = issueRes.getBody().data().couponId();

            // when - 주문 전체(130,000)에 쿠폰 적용
            PlaceOrderV1Request orderReq = new PlaceOrderV1Request(List.of(
                new PlaceOrderV1Request.OrderLineV1Request(product1Id, 2),
                new PlaceOrderV1Request.OrderLineV1Request(product2Id, 1)
            ), couponId);
            ResponseEntity<ApiResponse<OrderV1Response>> orderRes = restTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(orderReq, userHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then - 130,000의 10% = 13,000 할인
            OrderV1Response data = orderRes.getBody().data();
            assertAll(
                () -> assertThat(orderRes.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.totalAmount()).isEqualTo(130_000L),
                () -> assertThat(data.discountAmount()).isEqualTo(13_000L),
                () -> assertThat(data.finalAmount()).isEqualTo(117_000L)
            );
        }
    }
}
