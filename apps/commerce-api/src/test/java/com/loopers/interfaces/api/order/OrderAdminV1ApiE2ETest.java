package com.loopers.interfaces.api.order;

import com.loopers.application.order.CreateOrderCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.ProductStockService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderAdminV1ApiE2ETest {

    private static final String ENDPOINT_ORDERS = "/api-admin/v1/orders";
    private static final String ENDPOINT_ORDER_DETAIL = "/api-admin/v1/orders/{orderId}";
    private static final String HEADER_ADMIN_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP = "loopers.admin";

    private final TestRestTemplate testRestTemplate;
    private final BrandService brandService;
    private final ProductService productService;
    private final ProductStockService productStockService;
    private final OrderFacade orderFacade;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    OrderAdminV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandService brandService,
        ProductService productService,
        ProductStockService productStockService,
        OrderFacade orderFacade,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandService = brandService;
        this.productService = productService;
        this.productStockService = productStockService;
        this.orderFacade = orderFacade;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api-admin/v1/orders")
    @Nested
    class GetOrders {

        @DisplayName("어드민 헤더와 page, size가 주어지면 200 OK와 전체 주문 목록을 최신순 Page로 반환한다.")
        @Test
        void returnsOrdersByLatest_whenAdminHeaderAndPageQueryAreProvided() {
            // arrange
            Brand brand = createBrand();
            Product iphone = createProduct(brand, "아이폰 16 Pro", "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰", 1_550_000L, 10);
            Product iphoneMax = createProduct(brand, "아이폰 16 Pro Max", "더 큰 화면과 향상된 배터리를 제공하는 스마트폰", 1_900_000L, 5);
            OrderInfo firstOrder = createOrder(1L, iphone.getId(), 1);
            OrderInfo secondOrder = createOrder(2L, iphoneMax.getId(), 1);

            // act
            ResponseEntity<ApiResponse<PageResponse<OrderAdminV1Dto.OrderResponse>>> response = getOrders(adminHeaders());

            // assert
            PageResponse<OrderAdminV1Dto.OrderResponse> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.totalElements()).isEqualTo(2),
                () -> assertThat(data.content())
                    .extracting(OrderAdminV1Dto.OrderResponse::id)
                    .containsExactly(secondOrder.id(), firstOrder.id()),
                () -> assertThat(data.content())
                    .extracting(OrderAdminV1Dto.OrderResponse::userId)
                    .containsExactly(2L, 1L),
                () -> assertThat(data.content().get(0).items())
                    .extracting(OrderAdminV1Dto.OrderResponse.Item::productName)
                    .containsExactly("아이폰 16 Pro Max")
            );
        }

        @DisplayName("어드민 헤더가 없으면 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            // act
            ResponseEntity<ApiResponse<Object>> response = getOrdersForError(new HttpHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api-admin/v1/orders/{orderId}")
    @Nested
    class GetOrder {

        @DisplayName("어드민 헤더와 존재하는 주문 ID가 주어지면 200 OK와 주문 스냅샷 상세를 반환한다.")
        @Test
        void returnsOrderDetail_whenAdminHeaderAndOrderIdExist() {
            // arrange
            Brand brand = createBrand();
            Product iphone = createProduct(brand, "아이폰 16 Pro", "강력한 성능과 정교한 카메라 경험을 제공하는 스마트폰", 1_550_000L, 10);
            OrderInfo order = createOrder(1L, iphone.getId(), 2);

            // act
            ResponseEntity<ApiResponse<OrderAdminV1Dto.OrderResponse>> response = getOrder(order.id(), adminHeaders());

            // assert
            OrderAdminV1Dto.OrderResponse data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.id()).isEqualTo(order.id()),
                () -> assertThat(data.userId()).isEqualTo(1L),
                () -> assertThat(data.orderTotalPrice()).isEqualTo(3_100_000L),
                () -> assertThat(data.createdAt()).isNotNull(),
                () -> assertThat(data.updatedAt()).isNotNull(),
                () -> assertThat(data.items())
                    .extracting(OrderAdminV1Dto.OrderResponse.Item::brandName)
                    .containsExactly("애플"),
                () -> assertThat(data.items())
                    .extracting(OrderAdminV1Dto.OrderResponse.Item::productName)
                    .containsExactly("아이폰 16 Pro"),
                () -> assertThat(data.items())
                    .extracting(OrderAdminV1Dto.OrderResponse.Item::unitPrice)
                    .containsExactly(1_550_000L)
            );
        }

        @DisplayName("존재하지 않는 주문 ID가 주어지면 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenOrderDoesNotExist() {
            // act
            ResponseEntity<ApiResponse<Object>> response = getOrderForError(999_999L, adminHeaders());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    private Brand createBrand() {
        return brandService.createBrand("애플", "기술과 디자인으로 일상을 새롭게 만드는 브랜드");
    }

    private Product createProduct(Brand brand, String name, String description, long price, int stockQuantity) {
        Product product = productService.createProduct(brand.getId(), name, description, price);
        productStockService.createProductStock(product.getId(), stockQuantity);
        return product;
    }

    private OrderInfo createOrder(Long userId, Long productId, int quantity) {
        return orderFacade.createOrder(new CreateOrderCommand(userId, List.of(
            new CreateOrderCommand.Item(productId, quantity)
        )));
    }

    private ResponseEntity<ApiResponse<PageResponse<OrderAdminV1Dto.OrderResponse>>> getOrders(HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<PageResponse<OrderAdminV1Dto.OrderResponse>>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_ORDERS,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<Object>> getOrdersForError(HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_ORDERS,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType
        );
    }

    private ResponseEntity<ApiResponse<OrderAdminV1Dto.OrderResponse>> getOrder(Long orderId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<OrderAdminV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_ORDER_DETAIL,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType,
            orderId
        );
    }

    private ResponseEntity<ApiResponse<Object>> getOrderForError(Long orderId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ENDPOINT_ORDER_DETAIL,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            responseType,
            orderId
        );
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_ADMIN_LDAP, ADMIN_LDAP);
        return headers;
    }
}
