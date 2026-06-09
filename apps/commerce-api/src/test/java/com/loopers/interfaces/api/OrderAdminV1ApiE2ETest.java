package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.infrastructure.order.OrderItemJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderAdminV1ApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/orders";
    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP = "loopers.admin";
    private static final ParameterizedTypeReference<ApiResponse<Map<String, Object>>> MAP_RESPONSE = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OrderItemJpaRepository orderItemJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OrderModel saveOrder(Long userId) {
        return saveOrderAt(userId, ZonedDateTime.now());
    }

    private OrderModel saveOrderAt(Long userId, ZonedDateTime orderedAt) {
        OrderModel savedOrder = orderJpaRepository.save(OrderModel.builder()
            .userId(userId)
            .orderedAt(orderedAt)
            .totalPrice(78_000)
            .build());

        OrderItemModel orderItem = OrderItemModel.builder()
            .productId(10L)
            .productName("감성 가디건")
            .productBrandName("감성 브랜드")
            .unitPrice(39_000)
            .rawQuantity(2)
            .build();
        orderItem.assignOrder(savedOrder.getId());
        orderItemJpaRepository.save(orderItem);

        return savedOrder;
    }

    private HttpEntity<Void> adminGet() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(LDAP_HEADER, ADMIN_LDAP);

        return new HttpEntity<>(headers);
    }

    private HttpEntity<Void> guestGet() {
        return new HttpEntity<>(new HttpHeaders());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> contentOf(ResponseEntity<ApiResponse<Map<String, Object>>> response) {
        return (List<Map<String, Object>>) response.getBody().data().get("content");
    }

    @DisplayName("관리자 주문 목록 - GET /api-admin/v1/orders")
    @Nested
    class ReadOrders {

        @DisplayName("정상 요청이면, 200 OK와 함께 전체 주문 목록과 페이지 메타가 반환되고 주문 시각 내림차순으로 정렬된다.")
        @Test
        void returnsOk_withOrdersAndMeta() {
            // arrange
            ZonedDateTime earlier = ZonedDateTime.now().minusHours(1);
            ZonedDateTime later = ZonedDateTime.now();
            saveOrderAt(1L, earlier);
            OrderModel secondOrder = saveOrderAt(2L, later);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=20",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            Map<String, Object> firstItem = contentOf(response).get(0);
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data())
                    .containsKeys("content", "page", "size", "totalElements", "totalPages"),
                () -> assertThat(contentOf(response)).hasSize(2),
                () -> assertThat(firstItem).containsOnlyKeys("orderId", "userId", "status", "orderedAt", "totalPrice"),
                () -> assertThat(((Number) firstItem.get("userId")).longValue()).isEqualTo(secondOrder.getUserId()),
                () -> assertThat(firstItem.get("status")).isEqualTo("CREATED"),
                () -> assertThat(((Number) firstItem.get("totalPrice")).intValue()).isEqualTo(78_000),
                () -> assertThat(contentOf(response))
                    .extracting(o -> ZonedDateTime.parse((String) o.get("orderedAt")))
                    .isSortedAccordingTo(Comparator.reverseOrder())
            );
        }

        @DisplayName("주문이 없으면, 200 OK와 함께 빈 목록이 반환된다.")
        @Test
        void returnsOk_withEmptyContent() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=20",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(contentOf(response)).isEmpty()
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절된다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=20",
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode())
            );
        }
    }

    @DisplayName("관리자 주문 상세 - GET /api-admin/v1/orders/{orderId}")
    @Nested
    class ReadOrder {

        @DisplayName("정상 요청이면, 200 OK와 함께 회원 식별자와 항목 전체를 포함한 상세가 반환된다.")
        @Test
        void returnsOk_withDetail() {
            // arrange
            OrderModel order = saveOrder(7L);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + order.getId(),
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            Map<String, Object> data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(data).containsOnlyKeys("orderId", "userId", "status", "orderedAt", "totalPrice", "items"),
                () -> assertThat(((Number) data.get("userId")).longValue()).isEqualTo(7L)
            );
        }

        @DisplayName("관리자 인증 헤더가 없으면, 403 Forbidden으로 거절된다.")
        @Test
        void returnsForbidden_whenAdminHeaderIsMissing() {
            // arrange
            OrderModel order = saveOrder(7L);

            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + order.getId(),
                HttpMethod.GET,
                guestGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.FORBIDDEN.getCode())
            );
        }

        @DisplayName("존재하지 않는 주문이면, 404 Not Found로 거절된다.")
        @Test
        void returnsNotFound_whenOrderIsAbsent() {
            // act
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT + "/99999",
                HttpMethod.GET,
                adminGet(),
                MAP_RESPONSE
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.NOT_FOUND.getCode())
            );
        }
    }
}
