package com.loopers.order.interfaces.api;

import com.loopers.brand.application.BrandAdminService;
import com.loopers.brand.application.BrandCommand;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.product.application.ProductAdminService;
import com.loopers.product.application.ProductCommand;
import com.loopers.user.application.UserCommand;
import com.loopers.user.application.UserService;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String LOGIN_ID = "loopers01";
    private static final String RAW_PASSWORD = "Passw0rd!";

    private final TestRestTemplate testRestTemplate;
    private final UserService userService;
    private final BrandAdminService brandAdminService;
    private final ProductAdminService productAdminService;
    private final DatabaseCleanUp databaseCleanUp;

    private Long productId;
    private Long otherProductId;
    private Long deletedProductId;
    private Long suspendedProductId;
    private Long lowStockProductId;

    @Autowired
    public OrderV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            UserService userService,
            BrandAdminService brandAdminService,
            ProductAdminService productAdminService,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userService = userService;
        this.brandAdminService = brandAdminService;
        this.productAdminService = productAdminService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        userService.signUp(new UserCommand.SignUp(
                LOGIN_ID, RAW_PASSWORD, "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        ));
        Long brandId = brandAdminService.create(new BrandCommand.Create("루퍼스", "설명", null)).id();
        productId = productAdminService.create(new ProductCommand.Create(brandId, "셔츠", "설명", 29_000L, "thumb.jpg", 50)).id();
        otherProductId = productAdminService.create(new ProductCommand.Create(brandId, "바지", "설명", 15_000L, "thumb.jpg", 50)).id();
        lowStockProductId = productAdminService.create(new ProductCommand.Create(brandId, "한정판", "설명", 50_000L, "thumb.jpg", 1)).id();
        deletedProductId = productAdminService.create(new ProductCommand.Create(brandId, "삭제될 상품", "설명", 10_000L, "thumb.jpg", 10)).id();
        productAdminService.delete(deletedProductId);
        suspendedProductId = productAdminService.create(new ProductCommand.Create(brandId, "판매중지 상품", "설명", 20_000L, "thumb.jpg", 10)).id();
        productAdminService.suspend(suspendedProductId);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", RAW_PASSWORD);
        return headers;
    }

    private HttpHeaders noAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private OrderV1Request.Create orderOf(OrderV1Request.Create.Line... lines) {
        return new OrderV1Request.Create(
                List.of(lines),
                "김루퍼", "010-1234-5678", "12345", "서울시 강남구", "101동"
        );
    }

    private ResponseEntity<ApiResponse<OrderV1Response.Detail>> placeOrder(OrderV1Request.Create body, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<OrderV1Response.Detail>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange("/api/v1/orders", HttpMethod.POST, new HttpEntity<>(body, headers), type);
    }

    private ResponseEntity<ApiResponse<List<OrderV1Response.Summary>>> getMyOrders(HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<List<OrderV1Response.Summary>>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange("/api/v1/orders", HttpMethod.GET, new HttpEntity<>(headers), type);
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class PlaceOrder {

        @Test
        @DisplayName("인증된 사용자가 주문하면 200 과 PENDING 주문이 반환된다")
        void givenAuthenticatedUserAndAvailableProducts_whenPlaceOrder_thenReturnsPendingOrder() {
            ResponseEntity<ApiResponse<OrderV1Response.Detail>> response = placeOrder(
                    orderOf(
                            new OrderV1Request.Create.Line(productId, 2),
                            new OrderV1Request.Create.Line(otherProductId, 1)
                    ),
                    authHeaders()
            );

            OrderV1Response.Detail data = response.getBody().data();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(data.status()).isEqualTo("PENDING"),
                    () -> assertThat(data.orderNumber()).matches("\\d{8}-\\d{6}"),
                    () -> assertThat(data.totalAmount()).isEqualTo(29_000L * 2 + 15_000L),
                    () -> assertThat(data.items()).hasSize(2),
                    () -> assertThat(data.recipient().recipientName()).isEqualTo("김루퍼")
            );
        }

        @Test
        @DisplayName("주문하면 다시 내 주문 목록에서 조회된다")
        void givenPlacedOrder_whenGetMyOrders_thenContainsIt() {
            placeOrder(orderOf(new OrderV1Request.Create.Line(productId, 1)), authHeaders());

            ResponseEntity<ApiResponse<List<OrderV1Response.Summary>>> response = getMyOrders(authHeaders());

            assertThat(response.getBody().data())
                    .extracting(OrderV1Response.Summary::orderNumber)
                    .hasSize(1);
        }

        @Test
        @DisplayName("삭제된 상품을 주문하면 404 NOT_FOUND 를 받는다")
        void givenDeletedProduct_whenPlaceOrder_thenReturnsNotFound() {
            ResponseEntity<ApiResponse<OrderV1Response.Detail>> response = placeOrder(
                    orderOf(new OrderV1Request.Create.Line(deletedProductId, 1)), authHeaders()
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("판매중지(SUSPENDED) 상품을 주문하면 404 NOT_FOUND 를 받는다")
        void givenSuspendedProduct_whenPlaceOrder_thenReturnsNotFound() {
            ResponseEntity<ApiResponse<OrderV1Response.Detail>> response = placeOrder(
                    orderOf(new OrderV1Request.Create.Line(suspendedProductId, 1)), authHeaders()
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("재고보다 많은 수량을 주문하면 409 CONFLICT 를 받는다")
        void givenInsufficientStock_whenPlaceOrder_thenReturnsConflict() {
            ResponseEntity<ApiResponse<OrderV1Response.Detail>> response = placeOrder(
                    orderOf(new OrderV1Request.Create.Line(lowStockProductId, 5)), authHeaders()
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("주문 항목이 비어 있으면 400 BAD_REQUEST 를 받는다")
        void givenEmptyItems_whenPlaceOrder_thenReturnsBadRequest() {
            ResponseEntity<ApiResponse<OrderV1Response.Detail>> response = placeOrder(orderOf(), authHeaders());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("인증 헤더가 없으면 401 UNAUTHORIZED 를 받는다")
        void givenMissingHeaders_whenPlaceOrder_thenReturnsUnauthorized() {
            ResponseEntity<ApiResponse<OrderV1Response.Detail>> response = placeOrder(
                    orderOf(new OrderV1Request.Create.Line(productId, 1)), noAuthHeaders()
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    class GetMyOrders {

        @Test
        @DisplayName("주문이 없으면 빈 목록을 반환한다")
        void givenNoOrders_whenGetMyOrders_thenReturnsEmptyList() {
            ResponseEntity<ApiResponse<List<OrderV1Response.Summary>>> response = getMyOrders(authHeaders());

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data()).isEmpty()
            );
        }

        @Test
        @DisplayName("인증 헤더가 없으면 401 UNAUTHORIZED 를 받는다")
        void givenMissingHeaders_whenGetMyOrders_thenReturnsUnauthorized() {
            ResponseEntity<ApiResponse<List<OrderV1Response.Summary>>> response = getMyOrders(noAuthHeaders());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
