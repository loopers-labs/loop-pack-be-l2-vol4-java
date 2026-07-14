package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.User;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.order.OrderV1Controller;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.queue.QueueV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 게이트 on(행사 스위치) E2E — 대기열 진입 → polling → 토큰 발급(스케줄러) → 토큰으로 주문 성공까지 전체 흐름.
 * queue.order-gate.enabled=true 로 QueueAdmissionScheduler 도 함께 활성화된다.
 *
 * DirtiesContext(AFTER_CLASS): 이 컨텍스트가 캐시에 살아남으면 스케줄러가 백그라운드에서 계속 tick 하며
 * 같은 Redis 컨테이너를 쓰는 다른 대기열 테스트의 대기 유저를 입장시켜 버린다 — 클래스 종료 시 컨텍스트를 닫는다.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "queue.order-gate.enabled=true"
)
class QueueOrderGateE2ETest {

    private static final String ORDERS_ENDPOINT = "/api/v1/orders";
    private static final String ENTER_ENDPOINT = "/api/v1/queue/enter";
    private static final String POSITION_ENDPOINT = "/api/v1/queue/position";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    private Long productId;

    @Autowired
    public QueueOrderGateE2ETest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll(); // 자기완결 클린업 — 선행 테스트/기동 tick 잔재에 기대지 않는다
        userJpaRepository.save(new User(
            "tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", Gender.M));
        Brand brand = brandJpaRepository.save(new Brand("나이키", "Just Do It"));
        productId = productJpaRepository.save(new Product(brand.getId(), "에어맥스", "운동화", 1000L, 10)).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private HttpHeaders authHeaders(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.HEADER_LOGIN_ID, loginId);
        headers.set(AuthHeaders.HEADER_LOGIN_PW, "Password1!");
        return headers;
    }

    private ResponseEntity<ApiResponse<QueueV1Dto.QueueResponse>> queueCall(String loginId, String endpoint, HttpMethod method) {
        ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueueResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(endpoint, method, new HttpEntity<>(authHeaders(loginId)), responseType);
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createOrder(String loginId, String entryToken) {
        HttpHeaders headers = authHeaders(loginId);
        if (entryToken != null) {
            headers.set(OrderV1Controller.HEADER_ENTRY_TOKEN, entryToken);
        }
        OrderV1Dto.CreateOrderRequest request = new OrderV1Dto.CreateOrderRequest(
            List.of(new OrderV1Dto.OrderItemRequest(productId, 1)), null
        );
        ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> responseType =
            new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            ORDERS_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, headers), responseType
        );
    }

    @DisplayName("대기열 진입 → polling → 토큰 발급 → 토큰으로 주문하면 성공하고, 1회용 토큰은 소진된다.")
    @Test
    void fullFlow_enterPollTokenOrder() {
        // arrange — 대기열 진입
        ResponseEntity<ApiResponse<QueueV1Dto.QueueResponse>> entered =
            queueCall("tester01", ENTER_ENDPOINT, HttpMethod.POST);
        assertThat(entered.getStatusCode().is2xxSuccessful()).isTrue();

        // act 1 — 스케줄러(100ms 주기)가 입장시킬 때까지 polling → 토큰 확보
        AtomicReference<String> token = new AtomicReference<>();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            ResponseEntity<ApiResponse<QueueV1Dto.QueueResponse>> polled =
                queueCall("tester01", POSITION_ENDPOINT, HttpMethod.GET);
            assertThat(polled.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(polled.getBody().data().position()).isZero();
            assertThat(polled.getBody().data().token()).isNotBlank();
            token.set(polled.getBody().data().token());
        });

        // act 2 — 발급받은 토큰으로 주문
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderResponse = createOrder("tester01", token.get());

        // assert — 주문 성공 + 재고 차감 + 토큰 소진(대기열에도 없으므로 404)
        assertAll(
            () -> assertThat(orderResponse.getStatusCode().is2xxSuccessful()).isTrue(),
            () -> assertThat(orderResponse.getBody().data().totalAmount()).isEqualTo(1000L),
            () -> assertThat(productJpaRepository.findById(productId).orElseThrow().getStock()).isEqualTo(9),
            () -> assertThat(queueCall("tester01", POSITION_ENDPOINT, HttpMethod.GET).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND)
        );
    }

    @DisplayName("입장 토큰 없이 주문하면 400 으로 거부되고, 재고도 차감되지 않는다.")
    @Test
    void rejectsOrder_whenNoToken() {
        // act — 대기열을 거치지 않고 곧장 주문
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = createOrder("tester01", null);

        // assert
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
            () -> assertThat(productJpaRepository.findById(productId).orElseThrow().getStock()).isEqualTo(10)
        );
    }

    @DisplayName("위조된 토큰으로 주문하면 400 으로 거부된다.")
    @Test
    void rejectsOrder_whenForgedToken() {
        // act
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = createOrder("tester01", "forged-token");

        // assert
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
            () -> assertThat(productJpaRepository.findById(productId).orElseThrow().getStock()).isEqualTo(10)
        );
    }
}
