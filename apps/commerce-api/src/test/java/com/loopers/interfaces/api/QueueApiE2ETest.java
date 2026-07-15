package com.loopers.interfaces.api;

import com.loopers.application.user.UserService;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.user.UserModel;
import com.loopers.interfaces.api.queue.QueueDto;
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
import org.springframework.http.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueApiE2ETest {

    private static final String ENTER_URL = "/api/v1/queue/enter";
    private static final String POSITION_URL = "/api/v1/queue/position";
    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private UserModel savedUser;
    private HttpHeaders userHeaders;

    @BeforeEach
    void setUp() {
        savedUser = userService.signUp(new UserModel(
            "user01", "Password1!", "홍길동",
            LocalDate.of(1990, 1, 1), "user@example.com"
        ));

        userHeaders = new HttpHeaders();
        userHeaders.set(LOGIN_ID_HEADER, "user01");
        userHeaders.set(LOGIN_PW_HEADER, "Password1!");
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/queue/enter")
    @Nested
    class Enter {

        @DisplayName("로그인한 유저가 진입하면, 200과 순번을 반환한다.")
        @Test
        void returns200WithPosition_whenUserEnters() {
            // act
            ResponseEntity<ApiResponse<QueueDto.PositionResponse>> response = testRestTemplate.exchange(
                ENTER_URL, HttpMethod.POST,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().position()).isEqualTo(0L);
        }

        @DisplayName("비로그인 상태로 진입하면, 401을 반환한다.")
        @Test
        void returns401_whenNotLoggedIn() {
            // act
            ResponseEntity<ApiResponse<QueueDto.PositionResponse>> response = testRestTemplate.exchange(
                ENTER_URL, HttpMethod.POST,
                new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/queue/position")
    @Nested
    class Position {

        @DisplayName("대기열 진입 기록이 없으면, 404를 반환한다.")
        @Test
        void returns404_whenNeverEntered() {
            // act
            ResponseEntity<ApiResponse<QueueDto.PositionResponse>> response = testRestTemplate.exchange(
                POSITION_URL, HttpMethod.GET,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("입장 토큰이 이미 발급되어 있으면, 순번 0과 토큰을 반환한다.")
        @Test
        void returnsToken_whenAlreadyIssued() {
            // arrange
            testRestTemplate.exchange(
                ENTER_URL, HttpMethod.POST,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<ApiResponse<QueueDto.PositionResponse>>() {}
            );
            String issuedToken = entryTokenRepository.issue(savedUser.getId());

            // act
            ResponseEntity<ApiResponse<QueueDto.PositionResponse>> response = testRestTemplate.exchange(
                POSITION_URL, HttpMethod.GET,
                new HttpEntity<>(userHeaders),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().token()).isEqualTo(issuedToken);
            assertThat(response.getBody().data().position()).isEqualTo(0L);
        }
    }
}
