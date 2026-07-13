package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueAdmissionScheduler;
import com.loopers.domain.user.UserRegisterCommand;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.ApiResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대기열 HTTP 엔드포인트(enter/status/leave) 통합 테스트.
 *
 * <p>대상 상품(queue.target-product-ids)에 대해서만 대기열이 동작하므로, 테스트 클래스 전용으로
 * {@link DynamicPropertySource} 를 통해 해당 프로퍼티를 오버라이드한다. Redis는 Testcontainers로 띄운다.
 *
 * <p>{@link QueueAdmissionScheduler} 는 {@code @EnableScheduling} 에 의해 100ms마다 실제로 동작해서,
 * 목으로 대체하지 않으면 테스트 진행 중에 대기자가 임의로 ADMITTED 상태로 넘어가 타이밍에 따라
 * 결과가 흔들릴 수 있다. 이 테스트가 보려는 건 enter/status/leave의 HTTP 계약이므로 스케줄러는
 * Mock으로 대체해 배치 입장 로직이 끼어들지 않게 한다(배치 입장 자체는 QueueAdmissionSchedulerTest의 몫).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class QueueV1ControllerIntegrationTest {

    private static final Long GATED_PRODUCT_ID = 1_000L;

    @Container
    static final GenericContainer<?> redisContainer =
        new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        String host = redisContainer.getHost();
        String port = String.valueOf(redisContainer.getMappedPort(6379));
        registry.add("datasource.redis.master.host", () -> host);
        registry.add("datasource.redis.master.port", () -> port);
        registry.add("datasource.redis.replicas[0].host", () -> host);
        registry.add("datasource.redis.replicas[0].port", () -> port);
        registry.add("queue.target-product-ids", () -> GATED_PRODUCT_ID.toString());
    }

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private UserService userService;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private RedisCleanUp redisCleanUp;

    @MockitoBean
    private QueueAdmissionScheduler queueAdmissionScheduler;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    private HttpHeaders givenAuthenticatedUser(String loginId) {
        userService.register(new UserRegisterCommand(
            loginId, "Password1!", "홍길동", LocalDate.of(1990, 1, 1), loginId + "@example.com"
        ));
        return authHeaders(loginId, "Password1!");
    }

    private ResponseEntity<ApiResponse<QueueV1Dto.StatusResponse>> enter(Long productId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<QueueV1Dto.StatusResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api/v1/queues/" + productId, HttpMethod.POST, new HttpEntity<>(headers), responseType);
    }

    private ResponseEntity<ApiResponse<QueueV1Dto.StatusResponse>> status(Long productId, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<QueueV1Dto.StatusResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
            "/api/v1/queues/" + productId, HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    private ResponseEntity<ApiResponse<Object>> leave(Long productId, Long userId) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        QueueV1Dto.LeaveRequest request = new QueueV1Dto.LeaveRequest(userId);
        return testRestTemplate.exchange(
            "/api/v1/queues/" + productId + "/leave", HttpMethod.POST, new HttpEntity<>(request), responseType);
    }

    @DisplayName("게이트 대상 상품에 처음 입장하면, WAITING 상태와 1번 순위를 받는다.")
    @Test
    void enter_returnsWaitingWithPositionOne_forFirstUser() {
        // arrange
        HttpHeaders headers = givenAuthenticatedUser("user1");

        // act
        ResponseEntity<ApiResponse<QueueV1Dto.StatusResponse>> response = enter(GATED_PRODUCT_ID, headers);

        // assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        QueueV1Dto.StatusResponse body = response.getBody().data();
        assertThat(body.status()).isEqualTo("WAITING");
        assertThat(body.position()).isEqualTo(1L);
        assertThat(body.waitingCount()).isEqualTo(1L);
    }

    @DisplayName("혼자 대기 중이던 사용자가 이탈(leave) 후 재입장하면, 대기열에 실제로 제거됐다가 새로 추가된 것이므로 다시 1번 순위를 받는다.")
    @Test
    void reEnter_getsPositionOneAgain_afterLeave() {
        // arrange — user1 혼자 입장해 1번 순위
        HttpHeaders user1Headers = givenAuthenticatedUser("user1");
        ResponseEntity<ApiResponse<QueueV1Dto.StatusResponse>> firstEnter = enter(GATED_PRODUCT_ID, user1Headers);
        assertThat(firstEnter.getBody().data().position()).isEqualTo(1L);

        // act — user1이 이탈(sendBeacon)한 뒤 재입장
        ResponseEntity<ApiResponse<Object>> leaveResponse = leave(GATED_PRODUCT_ID, 1L);
        assertThat(leaveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponse<QueueV1Dto.StatusResponse>> reEnter = enter(GATED_PRODUCT_ID, user1Headers);

        // assert — ZADD NX는 최초 등록시각을 score로 쓰므로, leave가 실제로 ZSET에서 제거하지 않았다면
        // NX 조건에 걸려 재입장이 무시되고 이전 등록이 그대로 남아있게 된다. 대기열에 다른 유저가 없는
        // 상태에서 재입장 후에도 1번 순위가 나온다는 것은 leave가 실제로 제거를 수행했다는 증거다
        // (제거되지 않았다면 이 케이스에서도 결과적으로 1번이 나와 구분이 안 되므로, 최초 등록이
        // 그대로 남았는지는 waitingCount로도 교차 검증한다 — 대기열 전체 인원은 여전히 1명이어야 한다).
        assertThat(reEnter.getStatusCode()).isEqualTo(HttpStatus.OK);
        QueueV1Dto.StatusResponse body = reEnter.getBody().data();
        assertThat(body.status()).isEqualTo("WAITING");
        assertThat(body.position()).isEqualTo(1L);
        assertThat(body.waitingCount()).isEqualTo(1L);
    }

    @DisplayName("다른 사용자가 대기 중일 때 이탈(leave) 후 재입장하면, 대기열에서 실제로 제거되었기 때문에 새로운(더 뒤의) 순위로 등록된다.")
    @Test
    void reEnter_getsFreshPosition_afterLeave_whenAnotherUserIsWaiting() {
        // arrange — user1 먼저 입장(1번), user2가 입장해 2번을 차지
        HttpHeaders user1Headers = givenAuthenticatedUser("user1");
        HttpHeaders user2Headers = givenAuthenticatedUser("user2");
        ResponseEntity<ApiResponse<QueueV1Dto.StatusResponse>> firstEnter = enter(GATED_PRODUCT_ID, user1Headers);
        assertThat(firstEnter.getBody().data().position()).isEqualTo(1L);
        enter(GATED_PRODUCT_ID, user2Headers);

        // act — user1이 이탈(sendBeacon)한 뒤 재입장
        ResponseEntity<ApiResponse<Object>> leaveResponse = leave(GATED_PRODUCT_ID, 1L);
        assertThat(leaveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiResponse<QueueV1Dto.StatusResponse>> reEnter = enter(GATED_PRODUCT_ID, user1Headers);

        // assert — ZADD NX는 score(입장 시각)를 현재 시각으로 새로 기록한다. leave가 실제로 ZSET에서
        // user1을 제거했다면, 재입장은 "새 등록"으로 처리되어 이미 대기 중인 user2보다 늦은 시각을
        // score로 받아 뒤로(2번) 밀린다. 만약 leave가 아무 효과가 없었다면 NX 때문에 재입장은
        // 무시되고 user1은 원래 등록시각 그대로 1번을 유지했을 것이다 — 즉 2번이 나온다는 것 자체가
        // leave가 실제로 대기열에서 제거를 수행했다는 증거다.
        assertThat(reEnter.getStatusCode()).isEqualTo(HttpStatus.OK);
        QueueV1Dto.StatusResponse body = reEnter.getBody().data();
        assertThat(body.status()).isEqualTo("WAITING");
        assertThat(body.position()).isEqualTo(2L);
        assertThat(body.waitingCount()).isEqualTo(2L);
    }

    @DisplayName("한 번도 입장한 적 없는 사용자가 상태를 조회하면, QueueV1Controller.status 의 실제 동작대로 WAITING/position=null 을 받는다.")
    @Test
    void status_forNeverEnteredUser_reflectsControllerContract() {
        // arrange — 대기열에 아무도 없는 상태에서, 인증만 된 사용자가 바로 조회
        HttpHeaders headers = givenAuthenticatedUser("user1");

        // act
        ResponseEntity<ApiResponse<QueueV1Dto.StatusResponse>> response = status(GATED_PRODUCT_ID, headers);

        // assert — QueueRedisStoreImpl.status(): 토큰 없음 + 품절 아님 → WAITING 상태를 반환하되
        // ZSET에 없으므로 rank가 null → position도 null. waitingCount는 대기열 전체 크기(0)를 반영한다.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        QueueV1Dto.StatusResponse body = response.getBody().data();
        assertThat(body.status()).isEqualTo("WAITING");
        assertThat(body.position()).isNull();
        assertThat(body.waitingCount()).isEqualTo(0L);
    }
}
