package com.loopers.interfaces.api;

import com.loopers.fixture.UserFixture;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER  = "/api/v1/users";
    private static final String ENDPOINT_MY_INFO   = "/api/v1/users/me";
    private static final String ENDPOINT_CHANGE_PW = "/api/v1/users/me/password";

    /** 인증 헤더 — loginId + password (GET /me, PUT /me/password 공통) */
    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", UserFixture.LOGIN_ID);
        headers.set("X-Loopers-LoginPw", UserFixture.PASSWORD);
        return headers;
    }

    private void registerDefaultUser() {
        testRestTemplate.exchange(
            ENDPOINT_REGISTER, HttpMethod.POST,
            new HttpEntity<>(UserFixture.createRequest()),
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>>() {}
        );
    }

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

        @DisplayName("이미 존재하는 loginId 로 가입 시도 시, 409 Conflict 를 반환한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange — 먼저 한 번 정상 가입
            testRestTemplate.exchange(
                    ENDPOINT_REGISTER, HttpMethod.POST,
                    new HttpEntity<>(UserFixture.createRequest()),
                    new ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>>() {}
            );

            // act — 동일 loginId 로 재시도
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_REGISTER, HttpMethod.POST,
                            new HttpEntity<>(UserFixture.createRequest()),
                            new ParameterizedTypeReference<>() {}
                    );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("유효한 회원 정보로 가입 시, 200 + 유저 정보를 반환한다 (비밀번호 미포함).")
        @Test
        void returnsRegisteredUser_whenValidRequest() {
            // act
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response =
                    testRestTemplate.exchange(
                            ENDPOINT_REGISTER, HttpMethod.POST,
                            new HttpEntity<>(UserFixture.createRequest()),
                            new ParameterizedTypeReference<>() {}
                    );

            // assert — 200 + loginId/name/email 반환 확인
            //          RegisterResponse 에 password 필드 없음 → 컴파일 수준에서 보장
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo(UserFixture.LOGIN_ID),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(UserFixture.NAME),
                    () -> assertThat(response.getBody().data().email()).isEqualTo(UserFixture.EMAIL)
            );
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMyInfo {

        @DisplayName("올바른 인증 헤더로 조회 시, 200 + 마스킹된 이름을 반환한다.")
        @Test
        void returnsMyInfo_whenValidRequest() {
            // arrange
            registerDefaultUser();

            // act
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response =
                testRestTemplate.exchange(
                    ENDPOINT_MY_INFO, HttpMethod.GET,
                    new HttpEntity<>(authHeaders()),
                    new ParameterizedTypeReference<>() {}
                );

            // assert — 200 + 마스킹된 이름 "홍길*"
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*")
            );
        }

        @DisplayName("X-Loopers-LoginId 헤더 누락 시, 400 을 반환한다.")
        @Test
        void throwsBadRequest_whenLoginIdHeaderMissing() {
            // arrange — 헤더 없이 요청 (회원 존재 여부와 무관하게 Spring 이 400 반환)
            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_MY_INFO, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("loginId 가 영문/숫자 형식에 맞지 않으면, 400 을 반환한다.")
        @Test
        void throwsBadRequest_whenLoginIdFormatIsInvalid() {
            // arrange — 영문/숫자 외 문자 포함 (언더스코어) — HTTP 헤더는 ASCII 만 허용되므로 ASCII 범위의 규칙 위반값 사용
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "user_invalid");
            headers.set("X-Loopers-LoginPw", UserFixture.PASSWORD);

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_MY_INFO, HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 틀리면, 401 을 반환한다.")
        @Test
        void throwsUnauthorized_whenPasswordIsWrong() {
            // arrange
            registerDefaultUser();

            HttpHeaders wrongHeaders = new HttpHeaders();
            wrongHeaders.set("X-Loopers-LoginId", UserFixture.LOGIN_ID);
            wrongHeaders.set("X-Loopers-LoginPw", "WrongPass@1");

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_MY_INFO, HttpMethod.GET,
                    new HttpEntity<>(wrongHeaders),
                    new ParameterizedTypeReference<>() {}
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PUT /api/v1/users/me/password")
    @Nested
    class ChangePassword {

        @DisplayName("올바른 인증 헤더와 유효한 새 비밀번호로 변경 시, 200 을 반환한다.")
        @Test
        void returnsOk_whenValidRequest() {
            // arrange
            registerDefaultUser();
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("NewPass@99");

            // act — X-Loopers-LoginId + X-Loopers-LoginPw 헤더로 인증, 바디에 새 비밀번호
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_CHANGE_PW, HttpMethod.PUT,
                    new HttpEntity<>(request, authHeaders()),
                    new ParameterizedTypeReference<>() {}
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("비밀번호 헤더가 틀리면, 401 을 반환한다.")
        @Test
        void throwsUnauthorized_whenPasswordIsWrong() {
            // arrange
            registerDefaultUser();
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("NewPass@99");

            HttpHeaders wrongHeaders = new HttpHeaders();
            wrongHeaders.set("X-Loopers-LoginId", UserFixture.LOGIN_ID);
            wrongHeaders.set("X-Loopers-LoginPw", "WrongPass@1");

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_CHANGE_PW, HttpMethod.PUT,
                    new HttpEntity<>(request, wrongHeaders),
                    new ParameterizedTypeReference<>() {}
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("새 비밀번호가 규칙에 맞지 않으면, 400 을 반환한다.")
        @Test
        void throwsBadRequest_whenNewPasswordViolatesRule() {
            // arrange
            registerDefaultUser();
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("short");

            // act
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(
                    ENDPOINT_CHANGE_PW, HttpMethod.PUT,
                    new HttpEntity<>(request, authHeaders()),
                    new ParameterizedTypeReference<>() {}
                );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
