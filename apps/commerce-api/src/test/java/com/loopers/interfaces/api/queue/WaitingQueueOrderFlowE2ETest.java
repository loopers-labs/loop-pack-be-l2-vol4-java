package com.loopers.interfaces.api.queue;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.queue.QueueAdmissionRepository;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockRepository;
import com.loopers.domain.user.Gender;
import com.loopers.domain.user.PasswordEncryptor;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.user.AuthHeaders;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

// enter -> position polling -> 토큰 발급 -> POST /orders 전체 플로우가 200으로 완결되는지 검증한다(7.5).
// 실제 스케줄러(@Scheduled) 타이밍에 의존하지 않도록 QueueAdmissionRepository.admitBatch를 직접 호출해 토큰 발급을
// 결정론적으로 재현한다. test 프로필에서는 queue.scheduler-enabled=false 로 자동 tick 도 꺼져 있다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WaitingQueueOrderFlowE2ETest {

    private static final String LOGIN_ID = "user01";
    private static final String LOGIN_PW = "Password1!";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private QueueAdmissionRepository queueAdmissionRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private HttpEntity<Void> authHeaderEntity(String entryToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AuthHeaders.LOGIN_ID, LOGIN_ID);
        headers.set(AuthHeaders.LOGIN_PW, LOGIN_PW);
        if (entryToken != null) {
            headers.set(AuthHeaders.ENTRY_TOKEN, entryToken);
        }
        return new HttpEntity<>(null, headers);
    }

    @DisplayName("대기열 진입부터 주문 성공까지 전체 플로우가 완결된다.")
    @Test
    void completesFullFlow_fromQueueEntryToOrderSuccess() {
        // given
        UserModel user = userRepository.save(new UserModel(
                LOGIN_ID, LOGIN_PW, "홍길동", "1990-01-01", "user@example.com", Gender.MALE, passwordEncryptor
        ));
        BrandModel brand = brandRepository.save(new BrandModel("테스트브랜드"));
        ProductModel product = productRepository.save(new ProductModel(brand.getId(), "테스트상품", BigDecimal.valueOf(10000)));
        stockRepository.save(new StockModel(product.getId(), 5L));

        // 1. 대기열 진입
        ParameterizedTypeReference<ApiResponse<QueueV1Dto.EnterResponse>> enterResponseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> enterResponse = testRestTemplate.exchange(
                "/api/v1/queue/enter", HttpMethod.POST, authHeaderEntity(null), enterResponseType
        );
        assertTrue(enterResponse.getStatusCode().is2xxSuccessful());

        // 2. 순번 조회(polling) - 아직 토큰 없음
        ParameterizedTypeReference<ApiResponse<QueueV1Dto.PositionResponse>> positionResponseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> beforeAdmit = testRestTemplate.exchange(
                "/api/v1/queue/position", HttpMethod.GET, authHeaderEntity(null), positionResponseType
        );
        assertThat(beforeAdmit.getBody().data().token()).isNull();

        // 3. 스케줄러 대신 admitBatch를 직접 호출해 결정론적으로 토큰 발급
        List<QueueAdmissionRepository.AdmittedEntry> admitted = queueAdmissionRepository.admitBatch(1, TOKEN_TTL);
        assertThat(admitted).hasSize(1);
        String issuedToken = admitted.get(0).token();

        // 4. 순번 재조회 - 토큰이 채워져 있음
        ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> afterAdmit = testRestTemplate.exchange(
                "/api/v1/queue/position", HttpMethod.GET, authHeaderEntity(null), positionResponseType
        );
        assertThat(afterAdmit.getBody().data().token()).isEqualTo(issuedToken);

        // 5. 발급받은 토큰으로 주문
        HttpHeaders orderHeaders = new HttpHeaders();
        orderHeaders.setContentType(MediaType.APPLICATION_JSON);
        orderHeaders.set(AuthHeaders.LOGIN_ID, LOGIN_ID);
        orderHeaders.set(AuthHeaders.LOGIN_PW, LOGIN_PW);
        orderHeaders.set(AuthHeaders.ENTRY_TOKEN, issuedToken);
        OrderV1Dto.CreateRequest orderRequest = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1L)), null
        );
        ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>> orderResponseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> orderResponse = testRestTemplate.exchange(
                "/api/v1/orders", HttpMethod.POST, new HttpEntity<>(orderRequest, orderHeaders), orderResponseType
        );

        // then
        assertAll(
                () -> assertTrue(orderResponse.getStatusCode().is2xxSuccessful()),
                () -> assertThat(orderResponse.getBody().data().status()).isEqualTo("PLACED")
        );
    }
}
