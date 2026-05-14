package com.loopers.interfaces.api;

import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_SIGN_UP = "/api/v1/users";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    class SignUp {

        @DisplayName("정상 가입 요청이면, 2xx 응답과 생성된 유저 정보를 반환한다.")
        @Test
        void returnsCreatedUser_whenRequestIsValid() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "tester01", "Password1!", "홍길동", "1990-05-14", "test@example.com", "M"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request), responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(),
                () -> assertThat(response.getBody().data().id()).isPositive(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("tester01"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길동"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com"),
                () -> assertThat(response.getBody().data().gender()).isEqualTo("M")
            );
        }

        @DisplayName("성별이 누락되면, 400 Bad Request 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenGenderIsMissing() {
            // arrange
            Map<String, Object> request = new HashMap<>();
            request.put("loginId", "tester01");
            request.put("password", "Password1!");
            request.put("name", "홍길동");
            request.put("birthDate", "1990-05-14");
            request.put("email", "test@example.com");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response = testRestTemplate.exchange(
                ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request), responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
