package com.loopers.order.interfaces.api;

import com.loopers.brand.application.BrandAdminService;
import com.loopers.brand.application.BrandCommand;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.product.application.ProductAdminService;
import com.loopers.product.application.ProductCommand;
import com.loopers.queue.application.QueueService;
import com.loopers.queue.domain.EntryTokenStore;
import com.loopers.queue.interfaces.api.TokenGuard;
import com.loopers.user.application.UserAccountService;
import com.loopers.user.application.UserCommand;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderAdminV1ApiE2ETest {

    private static final String USER1 = "loopers01";
    private static final String USER2 = "loopers02";
    private static final String RAW_PASSWORD = "Passw0rd!";

    private final TestRestTemplate testRestTemplate;
    private final UserAccountService userAccountService;
    private final BrandAdminService brandAdminService;
    private final ProductAdminService productAdminService;
    private final QueueService queueService;
    private final EntryTokenStore entryTokenStore;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    private Long productId;
    private Long userId1;
    private Long userId2;

    @Autowired
    public OrderAdminV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            UserAccountService userAccountService,
            BrandAdminService brandAdminService,
            ProductAdminService productAdminService,
            QueueService queueService,
            EntryTokenStore entryTokenStore,
            DatabaseCleanUp databaseCleanUp,
            RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userAccountService = userAccountService;
        this.brandAdminService = brandAdminService;
        this.productAdminService = productAdminService;
        this.queueService = queueService;
        this.entryTokenStore = entryTokenStore;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @BeforeEach
    void setUp() {
        userId1 = userAccountService.signUp(new UserCommand.SignUp(
                USER1, RAW_PASSWORD, "김루퍼", LocalDate.of(1995, 3, 21), "looper1@example.com"
        )).id();
        userId2 = userAccountService.signUp(new UserCommand.SignUp(
                USER2, RAW_PASSWORD, "이루퍼", LocalDate.of(1996, 4, 22), "looper2@example.com"
        )).id();
        Long brandId = brandAdminService.create(new BrandCommand.Create("루퍼스", "설명", null)).id();
        productId = productAdminService.create(new ProductCommand.Create(brandId, "셔츠", "설명", 29_000L, "thumb.jpg", 100)).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private HttpHeaders userHeaders(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", RAW_PASSWORD);
        return headers;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Admin-Id", "admin01");
        return headers;
    }

    private void placeOrder(String loginId, Long userId) {
        OrderV1Request.Create body = new OrderV1Request.Create(
                List.of(new OrderV1Request.Create.Line(productId, 1)),
                "김루퍼", "010-1234-5678", "12345", "서울시 강남구", "101동",
                null
        );
        HttpHeaders headers = userHeaders(loginId);
        headers.set(TokenGuard.HEADER, provisionEntryToken(userId));
        testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST, new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<ApiResponse<OrderV1Response.Detail>>() {}
        );
    }

    private String provisionEntryToken(Long userId) {
        queueService.enter(String.valueOf(userId));
        queueService.admit();
        return entryTokenStore.find(String.valueOf(userId)).orElseThrow();
    }

    private ResponseEntity<ApiResponse<List<OrderV1Response.Summary>>> getAllOrders(HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<List<OrderV1Response.Summary>>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange("/api/v1/admin/orders", HttpMethod.GET, new HttpEntity<>(headers), type);
    }

    @Test
    @DisplayName("GET /api/v1/admin/orders: 관리자는 여러 사용자의 전체 주문을 조회한다")
    void givenOrdersFromMultipleUsers_whenGetAllOrders_thenReturnsAllOrders() {
        placeOrder(USER1, userId1);
        placeOrder(USER2, userId2);

        ResponseEntity<ApiResponse<List<OrderV1Response.Summary>>> response = getAllOrders(adminHeaders());

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).hasSize(2)
        );
    }

    @Test
    @DisplayName("GET /api/v1/admin/orders: 주문이 없으면 빈 목록을 반환한다")
    void givenNoOrders_whenGetAllOrders_thenReturnsEmptyList() {
        ResponseEntity<ApiResponse<List<OrderV1Response.Summary>>> response = getAllOrders(adminHeaders());

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).isEmpty()
        );
    }

    @Test
    @DisplayName("GET /api/v1/admin/orders: 관리자 인증이 없으면 401 UNAUTHORIZED 를 받는다")
    void givenNoAdminHeader_whenGetAllOrders_thenReturnsUnauthorized() {
        ResponseEntity<ApiResponse<List<OrderV1Response.Summary>>> response = getAllOrders(new HttpHeaders());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
