package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.user.AuthHeaders;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderAdminV1ApiE2ETest {

    private static final String BASE_URL = "/api-admin/v1/orders";
    private static final String ADMIN_LDAP = AuthHeaders.ADMIN_LDAP_VALUE;
    private static final String LOGIN_ID = "user01";
    private static final String LOGIN_PW = "Password1!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel saveUser() {
        return userRepository.save(new UserModel(LOGIN_ID, LOGIN_PW, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor));
    }

    private ProductModel saveProduct(String name, BigDecimal price) {
        BrandModel brand = brandRepository.save(new BrandModel("테스트브랜드"));
        return productRepository.save(new ProductModel(brand.getId(), name, price));
    }

    private void saveStock(Long productId, Long quantity) {
        stockRepository.save(new StockModel(productId, quantity));
    }

    private HttpEntity<Void> adminHeaderEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.LDAP, ADMIN_LDAP);
        return new HttpEntity<>(null, headers);
    }

    @DisplayName("GET /api-admin/v1/orders")
    @Nested
    class GetOrders {

        @DisplayName("주문이 존재하면 페이지네이션된 주문 목록을 반환한다.")
        @Test
        void returnsPagedOrders_whenOrdersExist() {
            // given
            saveUser();
            ProductModel product = saveProduct("테스트 상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 10L);
            orderFacade.createOrder(LOGIN_ID, LOGIN_PW, java.util.List.of(new OrderFacade.OrderItemDto(product.getId(), 1L)), null);
            orderFacade.createOrder(LOGIN_ID, LOGIN_PW, java.util.List.of(new OrderFacade.OrderItemDto(product.getId(), 2L)), null);

            // when
            ParameterizedTypeReference<ApiResponse<PageResponse<OrderAdminV1Dto.OrderListResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<OrderAdminV1Dto.OrderListResponse>>> response =
                    testRestTemplate.exchange(BASE_URL + "?page=0&size=20", HttpMethod.GET, adminHeaderEntity(), responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content().get(0).userId()).isNotNull()
            );
        }

        @DisplayName("주문이 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoOrdersExist() {
            // when
            ParameterizedTypeReference<ApiResponse<PageResponse<OrderAdminV1Dto.OrderListResponse>>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<OrderAdminV1Dto.OrderListResponse>>> response =
                    testRestTemplate.exchange(BASE_URL + "?page=0&size=20", HttpMethod.GET, adminHeaderEntity(), responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(0),
                    () -> assertThat(response.getBody().data().content()).isEmpty()
            );
        }

        @DisplayName("LDAP 헤더가 없으면 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenLdapHeaderIsMissing() {
            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL + "?page=0&size=20", HttpMethod.GET, new HttpEntity<>(null), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("잘못된 LDAP 값이면 403 Forbidden을 반환한다.")
        @Test
        void returnsForbidden_whenLdapIsInvalid() {
            // given
            HttpHeaders headers = new HttpHeaders();
            headers.set(AuthHeaders.LDAP, "invalid.ldap");

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL + "?page=0&size=20", HttpMethod.GET, new HttpEntity<>(null, headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @DisplayName("GET /api-admin/v1/orders/{orderId}")
    @Nested
    class GetOrder {

        @DisplayName("존재하는 주문 ID로 조회하면 userId를 포함한 주문 상세 정보를 반환하고 DB에 저장되어 있다.")
        @Test
        void returnsOrderDetail_whenOrderExists() {
            // given
            UserModel user = saveUser();
            ProductModel product = saveProduct("테스트 상품", BigDecimal.valueOf(10000));
            saveStock(product.getId(), 5L);
            Long orderId = orderFacade.createOrder(LOGIN_ID, LOGIN_PW, java.util.List.of(new OrderFacade.OrderItemDto(product.getId(), 2L)), null).id();

            // when
            ParameterizedTypeReference<ApiResponse<OrderAdminV1Dto.OrderDetailResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderAdminV1Dto.OrderDetailResponse>> response =
                    testRestTemplate.exchange(BASE_URL + "/" + orderId, HttpMethod.GET, adminHeaderEntity(), responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(orderId),
                    () -> assertThat(response.getBody().data().userId()).isEqualTo(user.getId()),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("PLACED"),
                    () -> assertThat(response.getBody().data().totalPrice()).isEqualByComparingTo(BigDecimal.valueOf(20000)),
                    () -> assertThat(response.getBody().data().items()).hasSize(1),
                    () -> assertThat(orderRepository.findById(orderId)).isPresent()
            );
        }

        @DisplayName("존재하지 않는 주문 ID로 조회하면 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenOrderDoesNotExist() {
            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL + "/999", HttpMethod.GET, adminHeaderEntity(), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("LDAP 헤더가 없으면 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenLdapHeaderIsMissing() {
            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL + "/1", HttpMethod.GET, new HttpEntity<>(null), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("잘못된 LDAP 값이면 403 Forbidden을 반환한다.")
        @Test
        void returnsForbidden_whenLdapIsInvalid() {
            // given
            HttpHeaders headers = new HttpHeaders();
            headers.set(AuthHeaders.LDAP, "invalid.ldap");

            // when
            ResponseEntity<Void> response =
                    testRestTemplate.exchange(BASE_URL + "/1", HttpMethod.GET, new HttpEntity<>(null, headers), Void.class);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
