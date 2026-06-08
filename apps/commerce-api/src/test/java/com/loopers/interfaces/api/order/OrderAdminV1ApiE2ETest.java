package com.loopers.interfaces.api.order;

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
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.apiadmin.order.OrderAdminV1Dto;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderAdminV1ApiE2ETest {

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductStockRepository productStockRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private static final String ADMIN_ID = "adminUser1";
    private static final String ADMIN_PW = "Admin1234!";
    private static final String USER_ID  = "orderUser1";
    private static final String USER_PW  = "Dlaxodid1!";

    private ProductStockModel savedStock;

    @BeforeEach
    void setUp() {
        userRepository.save(new UserModel(
                new UserId(ADMIN_ID),
                new Password(passwordEncoder.encode(ADMIN_PW)),
                new Name("관리자"),
                new BirthDay("1990-01-01"),
                new Email("admin@test.com"),
                UserRole.ADMIN
        ));
        userRepository.save(new UserModel(
                new UserId(USER_ID),
                new Password(passwordEncoder.encode(USER_PW)),
                new Name("주문유저"),
                new BirthDay("1990-01-01"),
                new Email("user@test.com"),
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

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", ADMIN_ID);
        headers.set("X-Loopers-LoginPw", ADMIN_PW);
        return headers;
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", USER_ID);
        headers.set("X-Loopers-LoginPw", USER_PW);
        return headers;
    }

    private Long createOrder(int quantity) {
        OrderV1Dto.OrderRequest request = new OrderV1Dto.OrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(savedStock.getId(), quantity))
        );
        ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange("/api/v1/orders", HttpMethod.POST, new HttpEntity<>(request, userHeaders()), responseType)
                .getBody().data().id();
    }

    @DisplayName("GET /api/v1/admin/orders")
    @Nested
    class GetOrders {

        @DisplayName("어드민 권한이면, 200 OK와 전체 주문 목록을 반환한다.")
        @Test
        void returnsAllOrders_whenAdminRequests() {
            createOrder(1);
            createOrder(2);

            ParameterizedTypeReference<ApiResponse<PageResponse<OrderAdminV1Dto.OrderResponse>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<PageResponse<OrderAdminV1Dto.OrderResponse>>> response =
                    testRestTemplate.exchange("/api/v1/admin/orders", HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().content()).hasSize(2);
        }
    }

    @DisplayName("GET /api/v1/admin/orders/{orderId}")
    @Nested
    class GetOrder {

        @DisplayName("어드민 권한이면, 200 OK와 주문 상세 정보를 반환한다.")
        @Test
        void returnsOrder_whenAdminRequests() {
            Long orderId = createOrder(1);

            ParameterizedTypeReference<ApiResponse<OrderAdminV1Dto.OrderResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderAdminV1Dto.OrderResponse>> response =
                    testRestTemplate.exchange("/api/v1/admin/orders/" + orderId, HttpMethod.GET, new HttpEntity<>(adminHeaders()), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isEqualTo(orderId);
        }
    }
}
