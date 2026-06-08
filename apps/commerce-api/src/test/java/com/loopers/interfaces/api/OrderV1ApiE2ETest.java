package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.vo.Money;
import com.loopers.domain.vo.Quantity;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OrderV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/orders";

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private BrandRepository brandRepository;
    @Autowired private ProductRepository productRepository;

    private Long productId;

    @BeforeEach
    void setUp() {
        BrandModel brand = brandRepository.save(new BrandModel("Nike", null));
        productId = productRepository.save(
                new ProductModel(brand.getId(), "운동화", null, Money.of(1000L), Quantity.of(10), null)).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpEntity<OrderV1Dto.OrderRequest> request(OrderV1Dto.OrderRequest body, Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (userId != null) {
            headers.add("X-Loopers-UserId", String.valueOf(userId));
        }
        return new HttpEntity<>(body, headers);
    }

    @DisplayName("주문을 생성하면 200 과 총액·PENDING 응답이 오고, 재고가 차감된다.")
    @Test
    void createOrder_success() {
        OrderV1Dto.OrderRequest body = new OrderV1Dto.OrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(productId, 2)));

        ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> type = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, request(body, 1L), type);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        OrderV1Dto.OrderResponse data = response.getBody().data();
        assertThat(data.totalAmount()).isEqualTo(2000L);   // 1000 × 2
        assertThat(data.status()).isEqualTo("PENDING");
        assertThat(data.items()).hasSize(1);
        assertThat(productRepository.findById(productId).get().getStockQuantity()).isEqualTo(Quantity.of(8));
    }

    @DisplayName("X-Loopers-UserId 헤더가 없으면 400 (BAD_REQUEST).")
    @Test
    void createOrder_failsWithoutUserHeader() {
        OrderV1Dto.OrderRequest body = new OrderV1Dto.OrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(productId, 1)));

        ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> type = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, request(body, null), type);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @DisplayName("재고보다 많은 수량을 주문하면 400 (BAD_REQUEST) 이고 재고는 차감되지 않는다.")
    @Test
    void createOrder_failsWhenStockInsufficient() {
        OrderV1Dto.OrderRequest body = new OrderV1Dto.OrderRequest(
                List.of(new OrderV1Dto.OrderItemRequest(productId, 999)));

        ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> type = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, request(body, 1L), type);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(productRepository.findById(productId).get().getStockQuantity()).isEqualTo(Quantity.of(10));
    }
}
