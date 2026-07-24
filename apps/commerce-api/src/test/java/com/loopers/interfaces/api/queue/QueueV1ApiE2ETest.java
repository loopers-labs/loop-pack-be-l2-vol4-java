package com.loopers.interfaces.api.queue;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    QueueV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private HttpHeaders authHeaders(String loginId, String loginPw) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", loginPw);
        return headers;
    }

    @DisplayName("POST /api/v1/queue/enter")
    @Nested
    class Enter {

        @DisplayName("처음 진입한 유저는 순번 0과 전체 인원 1을 반환받는다.")
        @Test
        void returnsRankZero_whenFirstToEnter() {
            // arrange
            userJpaRepository.save(new UserModel("user1", "pw1"));

            // act
            ResponseEntity<JsonNode> response = testRestTemplate.exchange(
                "/api/v1/queue/enter",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders("user1", "pw1")),
                JsonNode.class
            );

            // assert
            JsonNode body = response.getBody();
            assertNotNull(body);
            JsonNode data = body.get("data");
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(data.get("rank").asLong()).isEqualTo(0L),
                () -> assertThat(data.get("total").asLong()).isEqualTo(1L)
            );
        }
    }

    @DisplayName("GET /api/v1/queue/position")
    @Nested
    class GetPosition {

        @DisplayName("진입한 유저는 자신의 순번과 전체 인원을 조회할 수 있다.")
        @Test
        void returnsPosition_whenUserInQueue() {
            // arrange
            userJpaRepository.save(new UserModel("user1", "pw1"));
            testRestTemplate.exchange(
                "/api/v1/queue/enter", HttpMethod.POST,
                new HttpEntity<>(authHeaders("user1", "pw1")), JsonNode.class
            );

            // act
            ResponseEntity<JsonNode> response = testRestTemplate.exchange(
                "/api/v1/queue/position",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("user1", "pw1")),
                JsonNode.class
            );

            // assert
            JsonNode body = response.getBody();
            assertNotNull(body);
            JsonNode data = body.get("data");
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(data.get("rank").asLong()).isEqualTo(0L),
                () -> assertThat(data.get("total").asLong()).isEqualTo(1L)
            );
        }

        @DisplayName("대기열에 없는 유저가 순번을 조회하면 404 NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenUserNotInQueue() {
            // arrange
            userJpaRepository.save(new UserModel("user1", "pw1"));

            // act
            ResponseEntity<JsonNode> response = testRestTemplate.exchange(
                "/api/v1/queue/position",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders("user1", "pw1")),
                JsonNode.class
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
