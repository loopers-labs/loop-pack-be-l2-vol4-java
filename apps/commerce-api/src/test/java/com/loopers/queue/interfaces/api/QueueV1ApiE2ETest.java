package com.loopers.queue.interfaces.api;

import com.loopers.interfaces.api.ApiResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueV1ApiE2ETest {

    private static final String LOGIN_ID = "loopers01";
    private static final String RAW_PASSWORD = "Passw0rd!";
    private static final String ENTER = "/api/v1/queue/enter";
    private static final String POSITION = "/api/v1/queue/position";

    private final TestRestTemplate testRestTemplate;
    private final UserAccountService userAccountService;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public QueueV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            UserAccountService userAccountService,
            DatabaseCleanUp databaseCleanUp,
            RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userAccountService = userAccountService;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @BeforeEach
    void setUp() {
        userAccountService.signUp(new UserCommand.SignUp(
                LOGIN_ID, RAW_PASSWORD, "김루퍼", LocalDate.of(1995, 3, 21), "looper@example.com"
        ));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("진입하면 첫 유저는 순번 0을 받는다")
    void givenAuthedUser_whenEnter_thenPositionZero() {
        ResponseEntity<ApiResponse<QueueV1Response.Enter>> response = enter(authHeaders());

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().position()).isEqualTo(0L)
        );
    }

    @Test
    @DisplayName("진입 후 순번을 조회하면 순번과 예상 대기시간을 반환한다")
    void givenEnteredUser_whenPosition_thenReturnsRankAndEstimates() {
        enter(authHeaders());

        ResponseEntity<ApiResponse<QueueV1Response.Position>> response = position(authHeaders());

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().position()).isEqualTo(0L),
                () -> assertThat(response.getBody().data().pollAfterMs()).isEqualTo(1000L)
        );
    }

    @Test
    @DisplayName("인증 없이 진입하면 401")
    void givenNoAuth_whenEnter_thenUnauthorized() {
        ResponseEntity<ApiResponse<QueueV1Response.Enter>> response = enter(noAuthHeaders());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("진입하지 않고 순번을 조회하면 404")
    void givenNotEntered_whenPosition_thenNotFound() {
        ResponseEntity<ApiResponse<QueueV1Response.Position>> response = position(authHeaders());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<ApiResponse<QueueV1Response.Enter>> enter(HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<QueueV1Response.Enter>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(ENTER, HttpMethod.POST, new HttpEntity<>(headers), type);
    }

    private ResponseEntity<ApiResponse<QueueV1Response.Position>> position(HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<QueueV1Response.Position>> type = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(POSITION, HttpMethod.GET, new HttpEntity<>(headers), type);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", RAW_PASSWORD);
        return headers;
    }

    private HttpHeaders noAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
