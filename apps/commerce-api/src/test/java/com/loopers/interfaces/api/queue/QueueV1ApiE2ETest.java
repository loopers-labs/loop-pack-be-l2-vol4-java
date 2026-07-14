package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.WaitingQueueAdmitService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserDto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueV1ApiE2ETest {

    private static final String ENDPOINT_QUEUE = "/api/v1/queue";
    private static final String ENDPOINT_SIGNUP = "/api/v1/users";

    private final TestRestTemplate testRestTemplate;
    private final WaitingQueueAdmitService waitingQueueAdmitService;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    QueueV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        WaitingQueueAdmitService waitingQueueAdmitService,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.waitingQueueAdmitService = waitingQueueAdmitService;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("POST /api/v1/queue/enter")
    @Nested
    class Enter {

        @DisplayName("인증된 회원이 대기열에 진입하면, 현재 순번과 전체 대기 수를 반환한다.")
        @Test
        void returnsPosition_whenUserEntersQueue() {
            // arrange
            signup("user1234", "abc123!?");

            // act
            ResponseEntity<ApiResponse<QueueDto.Position.V1.Response>> response =
                testRestTemplate.exchange(
                    ENDPOINT_QUEUE + "/enter",
                    HttpMethod.POST,
                    new HttpEntity<>(authHeaders("user1234", "abc123!?")),
                    positionResponseType()
                );

            // assert
            QueueDto.Position.V1.Response data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.position()).isEqualTo(1L),
                () -> assertThat(data.waitingCount()).isEqualTo(1L),
                () -> assertThat(data.estimatedWaitSeconds()).isEqualTo(1L),
                () -> assertThat(data.pollingIntervalSeconds()).isEqualTo(1),
                () -> assertThat(data.entryToken()).isNull()
            );
        }

        @DisplayName("같은 회원이 중복 진입해도 대기열에는 한 번만 등록된다.")
        @Test
        void keepsSingleQueueEntry_whenSameUserEntersTwice() {
            // arrange
            signup("user1234", "abc123!?");
            enter("user1234", "abc123!?");

            // act
            ResponseEntity<ApiResponse<QueueDto.Position.V1.Response>> response =
                enter("user1234", "abc123!?");

            // assert
            QueueDto.Position.V1.Response data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.position()).isEqualTo(1L),
                () -> assertThat(data.waitingCount()).isEqualTo(1L)
            );
        }
    }

    @DisplayName("GET /api/v1/queue/position")
    @Nested
    class Position {

        @DisplayName("여러 회원이 진입하면, 진입 순서 기준으로 순번을 반환한다.")
        @Test
        void returnsPositionInEnterOrder_whenMultipleUsersEnterQueue() {
            // arrange
            signup("user1234", "abc123!?");
            signup("user5678", "abc123!?");
            enter("user1234", "abc123!?");
            enter("user5678", "abc123!?");

            // act
            ResponseEntity<ApiResponse<QueueDto.Position.V1.Response>> response =
                testRestTemplate.exchange(
                    ENDPOINT_QUEUE + "/position",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders("user5678", "abc123!?")),
                    positionResponseType()
                );

            // assert
            QueueDto.Position.V1.Response data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.position()).isEqualTo(2L),
                () -> assertThat(data.waitingCount()).isEqualTo(2L)
            );
        }

        @DisplayName("스케줄러가 입장을 허용한 회원은 순번 0과 입장 토큰을 반환한다.")
        @Test
        void returnsEntryToken_whenUserIsAdmitted() {
            // arrange
            signup("user1234", "abc123!?");
            enter("user1234", "abc123!?");
            waitingQueueAdmitService.admit();

            // act
            ResponseEntity<ApiResponse<QueueDto.Position.V1.Response>> response =
                testRestTemplate.exchange(
                    ENDPOINT_QUEUE + "/position",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders("user1234", "abc123!?")),
                    positionResponseType()
                );

            // assert
            QueueDto.Position.V1.Response data = response.getBody().data();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(data.position()).isZero(),
                () -> assertThat(data.waitingCount()).isZero(),
                () -> assertThat(data.estimatedWaitSeconds()).isZero(),
                () -> assertThat(data.entryToken()).isNotBlank()
            );
        }
    }

    private ResponseEntity<ApiResponse<QueueDto.Position.V1.Response>> enter(String loginId, String password) {
        return testRestTemplate.exchange(
            ENDPOINT_QUEUE + "/enter",
            HttpMethod.POST,
            new HttpEntity<>(authHeaders(loginId, password)),
            positionResponseType()
        );
    }

    private void signup(String loginId, String password) {
        UserDto.Register.V1.Request request = new UserDto.Register.V1.Request(
            loginId,
            password,
            "홍길동",
            LocalDate.of(1990, 1, 15),
            loginId + "@example.com"
        );
        testRestTemplate.postForEntity(ENDPOINT_SIGNUP, request, String.class);
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    private ParameterizedTypeReference<ApiResponse<QueueDto.Position.V1.Response>> positionResponseType() {
        return new ParameterizedTypeReference<>() {};
    }
}
