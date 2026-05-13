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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api/v1/users";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    class Register {

        @DisplayName("유효한 회원 정보로 가입 시, 200 + 유저 정보를 반환한다 (비밀번호 미포함).")
        @Test
        void returnsRegisteredUser_whenValidRequest() {
            // arrange
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "testuser", "Password@1", "홍길동", "1990-01-01", "test@loopers.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response =
                testRestTemplate.exchange(
                    ENDPOINT_REGISTER, HttpMethod.POST,
                    new HttpEntity<>(request),
                    responseType
                );

            // assert — 200 + loginId/name/email 반환 확인
            //          RegisterResponse 에 password 필드 없음 → 컴파일 수준에서 보장
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길동"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@loopers.com")
            );
        }
    }
}
