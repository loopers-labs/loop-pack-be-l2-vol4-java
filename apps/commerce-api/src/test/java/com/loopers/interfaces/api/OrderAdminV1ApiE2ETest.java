package com.loopers.interfaces.api;

import com.loopers.fixture.BrandFixture;
import com.loopers.fixture.ProductFixture;
import com.loopers.fixture.UserFixture;
import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.interfaces.api.common.response.ApiResponse;
import com.loopers.interfaces.api.common.response.PageResponse;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.product.ProductV1Dto;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderAdminV1ApiE2ETest {

    private static final String USERS_URL    = "/api/v1/users";
    private static final String BRANDS_URL   = "/api-admin/v1/brands";
    private static final String PRODUCTS_URL = "/api-admin/v1/products";
    private static final String ORDERS_URL   = "/api/v1/orders";
    private static final String ADMIN_ORDERS_URL = "/api-admin/v1/orders";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private UUID productId;

    @BeforeEach
    void setUp() {
        testRestTemplate.exchange(
            USERS_URL, HttpMethod.POST,
            new HttpEntity<>(UserFixture.createRequest()),
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>>() {}
        );

        ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> brandResp = testRestTemplate.exchange(
            BRANDS_URL, HttpMethod.POST,
            new HttpEntity<>(new BrandV1Dto.CreateRequest(BrandFixture.NAME, BrandFixture.DESCRIPTION)),
            new ParameterizedTypeReference<>() {}
        );
        UUID brandId = brandResp.getBody().data().id();

        ResponseEntity<ApiResponse<ProductV1Dto.AdminProductResponse>> productResp = testRestTemplate.exchange(
            PRODUCTS_URL, HttpMethod.POST,
            new HttpEntity<>(new ProductV1Dto.CreateRequest(
                brandId, ProductFixture.NAME, ProductFixture.DESCRIPTION, ProductFixture.PRICE, ProductFixture.INITIAL_QUANTITY
            )),
            new ParameterizedTypeReference<>() {}
        );
        productId = productResp.getBody().data().id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", UserFixture.LOGIN_ID);
        headers.set("X-Loopers-LoginPw", UserFixture.PASSWORD);
        return headers;
    }

    private void createOrder() {
        OrderV1Dto.ShippingInfoRequest shipping = new OrderV1Dto.ShippingInfoRequest(
            "홍길동", "010-1234-5678", "12345", "서울시 강남구 테헤란로 1", "101호"
        );
        OrderV1Dto.CreateRequest req = new OrderV1Dto.CreateRequest(
            shipping, List.of(new OrderV1Dto.OrderItemRequest(productId, 1))
        );
        testRestTemplate.exchange(
            ORDERS_URL, HttpMethod.POST,
            new HttpEntity<>(req, authHeaders()),
            new ParameterizedTypeReference<>() {}
        );
    }

    @DisplayName("GET /api-admin/v1/orders — 어드민 주문 목록 조회")
    @Nested
    class GetAdminOrderList {

        @DisplayName("주문이 있으면, 200 + 전체 주문 목록을 반환한다.")
        @Test
        void returnsList_whenOrdersExist() {
            // arrange
            createOrder();
            createOrder();

            // act
            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.AdminOrderResponse>>> response = testRestTemplate.exchange(
                ADMIN_ORDERS_URL + "?page=0&size=10", HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getTotalElements()).isEqualTo(2)
            );
        }

        @DisplayName("주문이 없으면, 200 + 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenNoOrders() {
            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.AdminOrderResponse>>> response = testRestTemplate.exchange(
                ADMIN_ORDERS_URL + "?page=0&size=10", HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().getTotalElements()).isEqualTo(0)
            );
        }
    }
}
