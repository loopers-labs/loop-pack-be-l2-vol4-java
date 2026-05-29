package com.loopers.interfaces.api;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.order.OrderCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.order.OrderStatus;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.order.admin.AdminOrderV1Dto;
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
class OrderReadV1ApiE2ETest {

    private static final String MEMBER_ENDPOINT = "/api/v1/orders";
    private static final String ADMIN_ENDPOINT = "/api/v1/admin/orders";
    private static final String LOGIN_ID = "user01";
    private static final String LOGIN_PW = "Abcd1234!";
    private static final String OTHER_LOGIN_ID = "user02";
    private static final String OTHER_LOGIN_PW = "Zxcv9876!";

    private final TestRestTemplate testRestTemplate;
    private final OrderFacade orderFacade;
    private final BrandFacade brandFacade;
    private final ProductFacade productFacade;
    private final UserFacade userFacade;
    private final DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long otherUserId;
    private Long productAId;

    @Autowired
    public OrderReadV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        OrderFacade orderFacade,
        BrandFacade brandFacade,
        ProductFacade productFacade,
        UserFacade userFacade,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.orderFacade = orderFacade;
        this.brandFacade = brandFacade;
        this.productFacade = productFacade;
        this.userFacade = userFacade;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        Long brandId = brandFacade.create("나이키", "Just Do It").id();
        productAId = productFacade.createProduct("에어맥스 270", "데일리 러닝화", 100_000L, 100, brandId).id();
        userId = userFacade.signUp(new UserCommand.SignUp(
            LOGIN_ID, LOGIN_PW, "김철수", LocalDate.of(1999, 3, 22), "user@example.com"
        )).id();
        otherUserId = userFacade.signUp(new UserCommand.SignUp(
            OTHER_LOGIN_ID, OTHER_LOGIN_PW, "이영희", LocalDate.of(1995, 7, 11), "other@example.com"
        )).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders(String loginId, String loginPw) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.LOGIN_ID, loginId);
        headers.set(AuthHeaders.LOGIN_PW, loginPw);
        return headers;
    }

    private Long placeOrder(Long buyerId, int quantity) {
        OrderCommand.Place command = new OrderCommand.Place(List.of(
            new OrderCommand.Line(productAId, quantity)
        ));
        return orderFacade.placeOrder(buyerId, command).id();
    }

    @DisplayName("GET /api/v1/orders (내 주문 목록)")
    @Nested
    class GetMyOrders {

        @DisplayName("기간 내 본인 주문이 최신순(id desc)으로 반환된다.")
        @Test
        void returnsOwnOrdersInLatestOrder_whenWithinPeriod() {
            // given
            Long firstOrderId = placeOrder(userId, 1);
            Long secondOrderId = placeOrder(userId, 2);
            String url = MEMBER_ENDPOINT + "?from=" + today() + "&to=" + today();

            // when
            ResponseEntity<ApiResponse<List<OrderV1Dto.MyOrderSummary>>> response = testRestTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(authHeaders(LOGIN_ID, LOGIN_PW)),
                new ParameterizedTypeReference<>() {});

            // then
            List<OrderV1Dto.MyOrderSummary> summaries = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(summaries).extracting(OrderV1Dto.MyOrderSummary::id)
                    .containsExactly(secondOrderId, firstOrderId),
                () -> assertThat(summaries.get(0).status()).isEqualTo(OrderStatus.CREATED)
            );
        }

        @DisplayName("다른 회원의 주문은 목록에 포함되지 않는다.")
        @Test
        void excludesOtherUsersOrders() {
            // given
            Long myOrderId = placeOrder(userId, 1);
            placeOrder(otherUserId, 1);
            String url = MEMBER_ENDPOINT + "?from=" + today() + "&to=" + today();

            // when
            ResponseEntity<ApiResponse<List<OrderV1Dto.MyOrderSummary>>> response = testRestTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(authHeaders(LOGIN_ID, LOGIN_PW)),
                new ParameterizedTypeReference<>() {});

            // then
            List<OrderV1Dto.MyOrderSummary> summaries = response.getBody().data();
            assertAll(
                () -> assertThat(summaries).hasSize(1),
                () -> assertThat(summaries.get(0).id()).isEqualTo(myOrderId)
            );
        }

        @DisplayName("기간 바깥의 주문은 제외되어 빈 목록을 반환한다.")
        @Test
        void excludesOrdersOutsidePeriod() {
            // given
            placeOrder(userId, 1);
            String url = MEMBER_ENDPOINT + "?from=" + LocalDate.now().minusDays(10)
                + "&to=" + LocalDate.now().minusDays(9);

            // when
            ResponseEntity<ApiResponse<List<OrderV1Dto.MyOrderSummary>>> response = testRestTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(authHeaders(LOGIN_ID, LOGIN_PW)),
                new ParameterizedTypeReference<>() {});

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).isEmpty()
            );
        }

        @DisplayName("시작일이 종료일보다 늦으면, BAD_REQUEST 와 INVALID_ORDER_PERIOD 코드를 반환한다.")
        @Test
        void returnsInvalidOrderPeriod_whenFromIsAfterTo() {
            // given
            String url = MEMBER_ENDPOINT + "?from=" + LocalDate.now()
                + "&to=" + LocalDate.now().minusDays(1);

            // when
            ResponseEntity<ApiResponse<List<OrderV1Dto.MyOrderSummary>>> response = testRestTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(authHeaders(LOGIN_ID, LOGIN_PW)),
                new ParameterizedTypeReference<>() {});

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("INVALID_ORDER_PERIOD")
            );
        }

        @DisplayName("시작일 파라미터가 누락되면, 경계에서 BAD_REQUEST 를 반환한다.")
        @Test
        void returnsBadRequest_whenFromIsMissing() {
            // given
            String url = MEMBER_ENDPOINT + "?to=" + today();

            // when
            ResponseEntity<ApiResponse<List<OrderV1Dto.MyOrderSummary>>> response = testRestTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(authHeaders(LOGIN_ID, LOGIN_PW)),
                new ParameterizedTypeReference<>() {});

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId} (내 주문 상세)")
    @Nested
    class GetMyOrder {

        @DisplayName("본인 주문의 상세를 스냅샷 그대로 반환한다.")
        @Test
        void returnsOwnOrderDetail() {
            // given
            Long orderId = placeOrder(userId, 2);

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.MyOrderDetail>> response = testRestTemplate.exchange(
                MEMBER_ENDPOINT + "/" + orderId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(LOGIN_ID, LOGIN_PW)),
                new ParameterizedTypeReference<>() {});

            // then
            OrderV1Dto.MyOrderDetail detail = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(detail.id()).isEqualTo(orderId),
                () -> assertThat(detail.totalAmount()).isEqualTo(200_000L),
                () -> assertThat(detail.items()).hasSize(1),
                () -> assertThat(detail.items().get(0).productName()).isEqualTo("에어맥스 270"),
                () -> assertThat(detail.items().get(0).brandName()).isEqualTo("나이키")
            );
        }

        @DisplayName("존재하지 않는 주문이면, NOT_FOUND 와 ORDER_NOT_FOUND 코드를 반환한다.")
        @Test
        void returnsOrderNotFound_whenOrderDoesNotExist() {
            // when
            ResponseEntity<ApiResponse<OrderV1Dto.MyOrderDetail>> response = testRestTemplate.exchange(
                MEMBER_ENDPOINT + "/999", HttpMethod.GET,
                new HttpEntity<>(authHeaders(LOGIN_ID, LOGIN_PW)),
                new ParameterizedTypeReference<>() {});

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("ORDER_NOT_FOUND")
            );
        }

        @DisplayName("타인의 주문에 접근하면, ORDER_NOT_FOUND 를 반환한다 (존재 자체 숨김).")
        @Test
        void returnsOrderNotFound_whenAccessingAnotherUsersOrder() {
            // given
            Long othersOrderId = placeOrder(otherUserId, 1);

            // when
            ResponseEntity<ApiResponse<OrderV1Dto.MyOrderDetail>> response = testRestTemplate.exchange(
                MEMBER_ENDPOINT + "/" + othersOrderId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(LOGIN_ID, LOGIN_PW)),
                new ParameterizedTypeReference<>() {});

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("ORDER_NOT_FOUND")
            );
        }
    }

    @DisplayName("GET /api/v1/admin/orders (어드민 전체 목록)")
    @Nested
    class GetAllOrders {

        @DisplayName("전체 회원의 주문이 페이지 단위로 반환되고 주문자 loginId 가 포함된다.")
        @Test
        void returnsAllOrdersWithBuyerLoginId() {
            // given
            placeOrder(userId, 1);
            placeOrder(otherUserId, 1);

            // when
            ResponseEntity<ApiResponse<AdminOrderV1Dto.PageResponse>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT + "?page=0&size=20", HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {});

            // then
            AdminOrderV1Dto.PageResponse body = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(body.totalElements()).isEqualTo(2),
                () -> assertThat(body.content()).extracting(AdminOrderV1Dto.AdminOrderSummary::buyerLoginId)
                    .containsExactlyInAnyOrder(LOGIN_ID, OTHER_LOGIN_ID)
            );
        }

        @DisplayName("page/size 미지정이면 0/20 기본값이 적용된다.")
        @Test
        void appliesDefaultPaging_whenNotSpecified() {
            // given
            placeOrder(userId, 1);

            // when
            ResponseEntity<ApiResponse<AdminOrderV1Dto.PageResponse>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {});

            // then
            AdminOrderV1Dto.PageResponse body = response.getBody().data();
            assertAll(
                () -> assertThat(body.page()).isEqualTo(0),
                () -> assertThat(body.size()).isEqualTo(20)
            );
        }

        @DisplayName("주문이 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyContent_whenNoOrders() {
            // when
            ResponseEntity<ApiResponse<AdminOrderV1Dto.PageResponse>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT, HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {});

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().content()).isEmpty(),
                () -> assertThat(response.getBody().data().totalElements()).isZero()
            );
        }
    }

    @DisplayName("GET /api/v1/admin/orders/{orderId} (어드민 주문 상세)")
    @Nested
    class GetOrder {

        @DisplayName("임의 회원의 주문 상세에 주문자 정보가 포함된다.")
        @Test
        void returnsOrderDetailWithBuyerInfo() {
            // given
            Long orderId = placeOrder(otherUserId, 3);

            // when
            ResponseEntity<ApiResponse<AdminOrderV1Dto.AdminOrderDetail>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT + "/" + orderId, HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {});

            // then
            AdminOrderV1Dto.AdminOrderDetail detail = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(detail.id()).isEqualTo(orderId),
                () -> assertThat(detail.userId()).isEqualTo(otherUserId),
                () -> assertThat(detail.buyerLoginId()).isEqualTo(OTHER_LOGIN_ID),
                () -> assertThat(detail.items()).hasSize(1),
                () -> assertThat(detail.totalAmount()).isEqualTo(300_000L)
            );
        }

        @DisplayName("존재하지 않는 주문이면, NOT_FOUND 와 ORDER_NOT_FOUND 코드를 반환한다.")
        @Test
        void returnsOrderNotFound_whenOrderDoesNotExist() {
            // when
            ResponseEntity<ApiResponse<AdminOrderV1Dto.AdminOrderDetail>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT + "/999", HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {});

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("ORDER_NOT_FOUND")
            );
        }
    }

    private String today() {
        return LocalDate.now().toString();
    }
}
