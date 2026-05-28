package com.loopers.interfaces.api;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderStatus;
import com.loopers.infrastructure.order.OrderItemJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.auth.AuthHeaders;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/orders";
    private static final String LOGIN_ID = "user01";
    private static final String LOGIN_PW = "Abcd1234!";

    private final TestRestTemplate testRestTemplate;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final UserFacade userFacade;
    private final OrderJpaRepository orderJpaRepository;
    private final OrderItemJpaRepository orderItemJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long productAId;

    @Autowired
    public OrderV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        UserFacade userFacade,
        OrderJpaRepository orderJpaRepository,
        OrderItemJpaRepository orderItemJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.userFacade = userFacade;
        this.orderJpaRepository = orderJpaRepository;
        this.orderItemJpaRepository = orderItemJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        Long brandId = brandFacade.create("나이키", "Just Do It").id();
        productAId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 100_000L, 10, brandId).id();
        userFacade.signUp(new UserCommand.SignUp(
            LOGIN_ID,
            LOGIN_PW,
            "김철수",
            LocalDate.of(1999, 3, 22),
            "user@example.com"
        ));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.LOGIN_ID, LOGIN_ID);
        headers.set(AuthHeaders.LOGIN_PW, LOGIN_PW);
        return headers;
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class PlaceOrder {

        @DisplayName("유효한 인증 헤더와 유효한 items 로 요청하면, 200 과 OrderResponse 를 반환하고 orders/order_items 가 생성된다.")
        @Test
        void returnsOrderResponse_whenAuthAndItemsAreValid() {
            // given
            OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(List.of(
                new OrderV1Dto.PlaceOrderRequest.Item(productAId, 2)
            ));

            // when
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()), responseType
            );

            // then
            Long orderId = response.getBody().data().id();
            List<OrderItem> savedItems = orderItemJpaRepository.findByOrderId(orderId);
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data().totalAmount()).isEqualTo(200_000L),
                () -> assertThat(response.getBody().data().status()).isEqualTo(OrderStatus.CREATED),
                () -> assertThat(response.getBody().data().items()).hasSize(1),
                () -> assertThat(response.getBody().data().items().get(0).productId()).isEqualTo(productAId),
                () -> assertThat(response.getBody().data().items().get(0).quantity()).isEqualTo(2),
                () -> assertThat(response.getBody().data().items().get(0).productName()).isEqualTo("에어맥스 270"),
                () -> assertThat(response.getBody().data().items().get(0).brandName()).isEqualTo("나이키"),
                () -> assertThat(orderJpaRepository.count()).isEqualTo(1L),
                () -> assertThat(savedItems).hasSize(1)
            );
        }

        @DisplayName("인증 헤더가 누락되면, UNAUTHORIZED 를 반환하고 orders 행이 생성되지 않는다.")
        @Test
        void returnsUnauthorized_whenAuthHeadersAreMissing() {
            // given
            OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(List.of(
                new OrderV1Dto.PlaceOrderRequest.Item(productAId, 1)
            ));

            // when
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, new HttpHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(orderJpaRepository.count()).isZero()
            );
        }

        @DisplayName("items 가 빈 배열이면, BAD_REQUEST 와 EMPTY_ORDER_ITEMS 코드를 반환한다.")
        @Test
        void returnsBadRequest_whenItemsIsEmpty() {
            // given
            OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(List.of());

            // when
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("EMPTY_ORDER_ITEMS"),
                () -> assertThat(orderJpaRepository.count()).isZero()
            );
        }

        @DisplayName("존재하지 않는 productId 로 요청하면, NOT_FOUND 와 PRODUCT_NOT_FOUND 코드를 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // given
            OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(List.of(
                new OrderV1Dto.PlaceOrderRequest.Item(999L, 1)
            ));

            // when
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("PRODUCT_NOT_FOUND"),
                () -> assertThat(orderJpaRepository.count()).isZero()
            );
        }

        @DisplayName("재고보다 많은 수량으로 요청하면, CONFLICT 와 OUT_OF_STOCK 코드를 반환한다.")
        @Test
        void returnsConflict_whenQuantityExceedsStock() {
            // given - 재고 10, 주문 15
            OrderV1Dto.PlaceOrderRequest request = new OrderV1Dto.PlaceOrderRequest(List.of(
                new OrderV1Dto.PlaceOrderRequest.Item(productAId, 15)
            ));

            // when
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()), responseType
            );

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("OUT_OF_STOCK"),
                () -> assertThat(orderJpaRepository.count()).isZero()
            );
        }
    }
}
