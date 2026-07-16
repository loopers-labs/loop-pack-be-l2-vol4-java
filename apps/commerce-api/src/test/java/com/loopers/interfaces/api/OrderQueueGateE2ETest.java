package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.user.BirthDate;
import com.loopers.domain.user.Email;
import com.loopers.domain.user.EncodedPassword;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.Name;
import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.inventory.InventoryJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.queue.QueueV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대기열 게이트(queue.enabled=true) E2E — 토큰 검증·소진·보존과 전체 흐름(진입→발급→주문).
 * 게이트 off 경로(무토큰 주문 성공)는 기존 {@code OrderV1ApiE2ETest} 전체가 test 프로필
 * 기본값(enabled=false)으로 이미 증명한다.
 *
 * <p>@DirtiesContext: 이 컨텍스트의 스케줄러가 캐시에 살아남으면 이후 다른 테스트 클래스가
 * 공유 Redis 에 세운 대기열을 백그라운드로 pop 해 오염시키므로, 클래스 종료 시 컨텍스트를 닫는다.</p>
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "queue.enabled=true",
                "queue.scheduler.interval-ms=100",
                "queue.scheduler.batch-size=5",
        }
)
class OrderQueueGateE2ETest {

    private static final String ORDERS = "/api/v1/orders";
    private static final String ENTER = "/api/v1/queue/enter";
    private static final String POSITION = "/api/v1/queue/position";
    private static final String PASSWORD = "Abcd123!";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final InventoryJpaRepository inventoryJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntryTokenRepository entryTokenRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    private Long productId;
    private Long user1Id;

    @Autowired
    public OrderQueueGateE2ETest(
            TestRestTemplate testRestTemplate,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            InventoryJpaRepository inventoryJpaRepository,
            UserJpaRepository userJpaRepository,
            PasswordEncoder passwordEncoder,
            EntryTokenRepository entryTokenRepository,
            DatabaseCleanUp databaseCleanUp,
            RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.inventoryJpaRepository = inventoryJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.passwordEncoder = passwordEncoder;
        this.entryTokenRepository = entryTokenRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @BeforeEach
    void setUp() {
        Brand brand = brandJpaRepository.save(Brand.create("브랜드A", "소개"));
        productId = productJpaRepository.save(Product.create(brand.getId(), "상품A", Money.of(1_000L))).getId();
        inventoryJpaRepository.save(Inventory.create(productId, 10));
        user1Id = createUser("user1");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private Long createUser(String loginId) {
        UserModel user = new UserModel(
                new LoginId(loginId),
                new Name("유저"),
                new BirthDate(LocalDate.of(1999, 1, 1)),
                new Email(loginId + "@loopers.com"),
                EncodedPassword.create(passwordEncoder, PASSWORD)
        );
        return userJpaRepository.save(user).getId();
    }

    private static HttpHeaders loginHeader(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Loopers-LoginId", loginId);
        headers.add("X-Loopers-LoginPw", PASSWORD);
        return headers;
    }

    private ResponseEntity<ApiResponse<OrderV1Dto.CreatedResponse>> placeOrder(String loginId, String entryToken) {
        HttpHeaders headers = loginHeader(loginId);
        if (entryToken != null) {
            headers.add("X-Entry-Token", entryToken);
        }
        OrderV1Dto.PlaceRequest request = new OrderV1Dto.PlaceRequest(
                null, List.of(new OrderV1Dto.LineRequest(productId, 1)));
        ParameterizedTypeReference<ApiResponse<OrderV1Dto.CreatedResponse>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ORDERS, HttpMethod.POST, new HttpEntity<>(request, headers), type);
    }

    @DisplayName("게이트 차단 — 유효한 토큰 없이는 주문할 수 없다")
    @Nested
    class Blocked {

        @DisplayName("토큰 없이 주문하면 403 을 반환한다.")
        @Test
        void rejectsOrderWithoutToken() {
            ResponseEntity<ApiResponse<OrderV1Dto.CreatedResponse>> response = placeOrder("user1", null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("발급된 토큰과 다른 값이면 403 을 반환한다.")
        @Test
        void rejectsOrderWithWrongToken() {
            entryTokenRepository.issue(user1Id, "real-token", Duration.ofMinutes(5));

            ResponseEntity<ApiResponse<OrderV1Dto.CreatedResponse>> response = placeOrder("user1", "fake-token");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("TTL 이 지나 만료된 토큰으로는 주문할 수 없다.")
        @Test
        void rejectsOrderWithExpiredToken() throws InterruptedException {
            entryTokenRepository.issue(user1Id, "short-lived", Duration.ofMillis(300));
            Thread.sleep(500);

            ResponseEntity<ApiResponse<OrderV1Dto.CreatedResponse>> response = placeOrder("user1", "short-lived");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("게이트는 주문 생성(POST)만 막는다 — 목록 조회(GET)는 토큰 없이 가능하다.")
        @Test
        void allowsGetOrdersWithoutToken() {
            ResponseEntity<String> response = testRestTemplate.exchange(
                    ORDERS, HttpMethod.GET, new HttpEntity<>(loginHeader("user1")), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("토큰 라이프사이클 — 소진과 보존")
    @Nested
    class TokenLifecycle {

        @DisplayName("유효한 토큰으로 주문하면 성공하고, 토큰은 소진되어 재주문 시 403 이다.")
        @Test
        void consumesTokenOnSuccessfulOrder() {
            entryTokenRepository.issue(user1Id, "valid-token", Duration.ofMinutes(5));

            ResponseEntity<ApiResponse<OrderV1Dto.CreatedResponse>> first = placeOrder("user1", "valid-token");

            assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(first.getBody().data().id()).isNotNull();
            assertThat(entryTokenRepository.find(user1Id)).isEmpty();

            ResponseEntity<ApiResponse<OrderV1Dto.CreatedResponse>> second = placeOrder("user1", "valid-token");
            assertThat(second.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("주문이 실패하면 토큰을 보존한다 — TTL 내 재시도에 새 대기가 필요 없다.")
        @Test
        void preservesTokenOnFailedOrder() {
            entryTokenRepository.issue(user1Id, "valid-token", Duration.ofMinutes(5));
            HttpHeaders headers = loginHeader("user1");
            headers.add("X-Entry-Token", "valid-token");
            // 재고(10)보다 많은 수량 → 주문 실패
            OrderV1Dto.PlaceRequest overStock = new OrderV1Dto.PlaceRequest(
                    null, List.of(new OrderV1Dto.LineRequest(productId, 999)));
            ParameterizedTypeReference<ApiResponse<OrderV1Dto.CreatedResponse>> type = new ParameterizedTypeReference<>() {};

            ResponseEntity<ApiResponse<OrderV1Dto.CreatedResponse>> response =
                    testRestTemplate.exchange(ORDERS, HttpMethod.POST, new HttpEntity<>(overStock, headers), type);

            assertThat(response.getStatusCode().is2xxSuccessful()).isFalse();
            assertThat(entryTokenRepository.find(user1Id)).contains("valid-token");
        }

        @DisplayName("이미 처리 중인 주문이 있으면 같은 토큰의 동시 주문을 409 로 막고, 토큰은 보존한다.")
        @Test
        void rejectsConcurrentOrderWhileInFlight() {
            entryTokenRepository.issue(user1Id, "valid-token", Duration.ofMinutes(5));
            // 선행 주문이 아직 처리 중(afterCompletion 전)인 상황을 in-flight 마크로 재현
            entryTokenRepository.acquireInFlight(user1Id, "prior-order", Duration.ofSeconds(30));

            ResponseEntity<ApiResponse<OrderV1Dto.CreatedResponse>> response = placeOrder("user1", "valid-token");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            // 거절된 동시 요청은 선행 요청의 마크·토큰을 건드리지 않는다 → 토큰 보존
            assertThat(entryTokenRepository.find(user1Id)).contains("valid-token");
        }
    }

    @DisplayName("전체 흐름 — 진입 → 스케줄러 발급 → 폴링으로 토큰 수신 → 주문")
    @Nested
    class FullFlow {

        @DisplayName("대기열 진입 후 폴링으로 받은 토큰으로 주문에 성공한다.")
        @Test
        void enterPollAndOrder() throws InterruptedException {
            ParameterizedTypeReference<ApiResponse<QueueV1Dto.PositionResponse>> queueType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENTER, HttpMethod.POST, new HttpEntity<>(loginHeader("user1")), queueType);

            String token = null;
            long deadline = System.currentTimeMillis() + 5_000;
            while (token == null && System.currentTimeMillis() < deadline) {
                ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> polled = testRestTemplate.exchange(
                        POSITION, HttpMethod.GET, new HttpEntity<>(loginHeader("user1")), queueType);
                token = polled.getBody().data().token();
                if (token == null) {
                    Thread.sleep(100);
                }
            }
            assertThat(token).isNotNull();

            ResponseEntity<ApiResponse<OrderV1Dto.CreatedResponse>> response = placeOrder("user1", token);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(entryTokenRepository.find(user1Id)).isEmpty();
        }
    }
}
