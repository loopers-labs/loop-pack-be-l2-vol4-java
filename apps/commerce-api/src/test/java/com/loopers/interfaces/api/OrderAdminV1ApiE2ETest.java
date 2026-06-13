package com.loopers.interfaces.api;

import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductStockEntity;
import com.loopers.infrastructure.product.ProductStockJpaRepository;
import com.loopers.interfaces.api.order.OrderAdminV1Dto;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderAdminV1ApiE2ETest {

    private static final String ADMIN_ORDERS_URL = "/api-admin/v1/orders";
    private static final String ORDERS_URL = "/api/v1/orders";
    private static final String USERS_URL = "/api/v1/users";

    private static final String LOGIN_ID = "adminorderuser";
    private static final String LOGIN_PW = "pAssWord1!";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private BrandJpaRepository brandJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private ProductStockJpaRepository productStockJpaRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @BeforeEach
    void setUp() {
        testRestTemplate.exchange(
            USERS_URL, HttpMethod.POST,
            new HttpEntity<>(new UserV1Dto.UserJoinRequest(LOGIN_ID, LOGIN_PW, "루퍼스", LocalDate.of(2000, 1, 1), "adminorder@test.com")),
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
        );

        BrandEntity brand = brandJpaRepository.save(new BrandEntity("브랜드", "설명"));
        ProductEntity product = productJpaRepository.save(new ProductEntity(brand.getId(), "청바지", BigDecimal.valueOf(50000)));
        productStockJpaRepository.save(new ProductStockEntity(product.getId(), 10L));
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    private HttpHeaders userAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", LOGIN_PW);
        return headers;
    }

    private Long createOrder() {
        OrderV1Dto.OrderCreateRequest request = new OrderV1Dto.OrderCreateRequest(
            List.of(new OrderV1Dto.OrderCreateRequest.Item(productId, 2)), null
        );
        ResponseEntity<ApiResponse<OrderV1Dto.OrderCreateResponse>> response = testRestTemplate.exchange(
            ORDERS_URL, HttpMethod.POST,
            new HttpEntity<>(request, userAuthHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().orderId();
    }

    @DisplayName("GET /api-admin/v1/orders")
    @Nested
    class GetOrdersForAdmin {

        @DisplayName("어드민 헤더가 있으면, 전체 주문 목록을 페이징으로 반환한다.")
        @Test
        void returnsPagedOrderList_whenAdminHeaderIsPresent() {
            createOrder();
            createOrder();

            ResponseEntity<ApiResponse<Map>> response = testRestTemplate.exchange(
                ADMIN_ORDERS_URL + "?page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().get("totalElements")).isEqualTo(2)
            );
        }

        @DisplayName("어드민 헤더가 없으면, 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            ResponseEntity<ApiResponse<Map>> response = testRestTemplate.exchange(
                ADMIN_ORDERS_URL + "?page=0&size=20",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api-admin/v1/orders/{orderId}")
    @Nested
    class GetOrder {

        @DisplayName("존재하는 주문을 조회하면, userId를 포함한 주문 상세 정보를 반환한다.")
        @Test
        void returnsOrderDetailWithUserId_whenOrderExists() {
            Long orderId = createOrder();

            ResponseEntity<ApiResponse<OrderAdminV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ADMIN_ORDERS_URL + "/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().orderId()).isEqualTo(orderId),
                () -> assertThat(response.getBody().data().userId()).isNotNull(),
                () -> assertThat(response.getBody().data().items()).hasSize(1)
            );
        }

        @DisplayName("존재하지 않는 주문을 조회하면, 404를 반환한다.")
        @Test
        void returnsNotFound_whenOrderDoesNotExist() {
            ResponseEntity<ApiResponse<OrderAdminV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ADMIN_ORDERS_URL + "/9999",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("X-Loopers-Ldap 헤더가 없으면, 401을 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderIsMissing() {
            Long orderId = createOrder();

            ResponseEntity<ApiResponse<OrderAdminV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                ADMIN_ORDERS_URL + "/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
