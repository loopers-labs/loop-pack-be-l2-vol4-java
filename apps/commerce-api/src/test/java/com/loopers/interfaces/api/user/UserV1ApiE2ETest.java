package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_SIGN_UP = "/api/v1/users";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    UserV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
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

        @DisplayName("유효한 회원 정보를 보내면, 201 CREATED 와 가입된 회원 정보를 반환한다.")
        @Test
        void returnsCreatedUser_whenValidRequestIsProvided() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "loopers01", "Loopers!2026", "김성호", LocalDate.of(1993, 11, 3), "loopers@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("loopers01"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("김성호"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("loopers@example.com"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(1993, 11, 3))
            );
        }

        @DisplayName("이미 가입된 로그인 ID 로 요청하면, 409 CONFLICT 응답을 받는다.")
        @Test
        void returnsConflict_whenLoginIdAlreadyExists() {
            // arrange
            UserV1Dto.SignUpRequest first = new UserV1Dto.SignUpRequest(
                "loopers01", "Loopers!2026", "김성호", LocalDate.of(1993, 11, 3), "loopers@example.com"
            );
            testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(first),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {});

            UserV1Dto.SignUpRequest duplicate = new UserV1Dto.SignUpRequest(
                "loopers01", "Different!9999", "홍길동", LocalDate.of(1991, 1, 1), "other@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(duplicate), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("비밀번호 정책에 맞지 않으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenPasswordViolatesPolicy() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "loopers01", "short", "김성호", LocalDate.of(1993, 11, 3), "loopers@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
