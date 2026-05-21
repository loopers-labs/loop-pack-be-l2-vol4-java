package com.loopers.interfaces.api;

import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class UserV1ApiE2ETest {

    private static final String ENDPOINT_SIGNUP = "/api/v1/users";
    private static final String ENDPOINT_GET_MY_INFO = "/api/v1/users/me";
    private static final String ENDPOINT_CHANGE_PASSWORD = "/api/v1/users/me/password";

    private static final String VALID_LOGIN_ID = "testId";
    private static final String VALID_PW = "validPassword123";
    private static final String VALID_NAME = "임찬빈";
    private static final String VALID_BIRTH_DATE = "1998-04-11";
    private static final String VALID_EMAIL = "test@test.com";

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
    class SignUp {
        @DisplayName("정상 정보로 회원 가입 시, 생성된 유저 정보를 응답으로 변환한다.")
        @Test
        void returnUserInfo_whenValidInfoProvided(){
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    VALID_LOGIN_ID,VALID_PW,VALID_NAME,VALID_BIRTH_DATE,VALID_EMAIL);

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
                    new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST,new HttpEntity<>(request),responseType);

            //assert
            assertAll(
                    ()->assertTrue(response.getStatusCode().is2xxSuccessful()),
                    ()->assertThat(response.getBody().data().loginId()).isEqualTo(VALID_LOGIN_ID),
                    ()->assertThat(response.getBody().data().name()).isEqualTo(VALID_NAME),
                    ()->assertThat(response.getBody().data().email()).isEqualTo(VALID_EMAIL)
            );
        }
        @DisplayName("이미 가입된 ID 로 회원가입 시도하면 409 CONFLICT 응답을 받는다.")
        @Test
        void returnsConflict_whenLoginIdAlreadyExists() {
            // arrange — 같은 ID 로 한 번 가입
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    VALID_LOGIN_ID, VALID_PW, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
           // 일단 실행만
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // act — 같은 ID 로 또 가입
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("잘못된 형식(빈 이름)으로 회원가입 시도하면 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    VALID_LOGIN_ID, VALID_PW, "", VALID_BIRTH_DATE, VALID_EMAIL
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMyInfo {

        private void signUpDefault() {
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
            VALID_LOGIN_ID,VALID_PW,VALID_NAME,VALID_BIRTH_DATE,VALID_EMAIL);
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request),
                    new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {});
        }

        @DisplayName("정상 헤더로 조회하면, 본인 정보를 반환한다 (이름은 마스킹).")
        @Test
        void returnsMyInfo_whenValidHeaders() {
            // arrange
            signUpDefault();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", VALID_LOGIN_ID);
            headers.set("X-Loopers-LoginPw", VALID_PW);

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_GET_MY_INFO, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo(VALID_LOGIN_ID),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("임찬*"),   // 마스킹 확인
                    () -> assertThat(response.getBody().data().email()).isEqualTo(VALID_EMAIL)
            );
        }

        @DisplayName("존재하지 않는 loginId 로 조회하면 404 NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenLoginIdDoesNotExist() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "noSuchUser");
            headers.set("X-Loopers-LoginPw", "anyPassword123");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_GET_MY_INFO, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("X-Loopers-LoginId 헤더가 없으면 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenLoginIdHeaderMissing() {
            // arrange — LoginPw 만 보냄
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginPw", VALID_PW);

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_GET_MY_INFO, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password")
    @Nested
    class ChangePassword {

        private void signUpDefault() {
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    VALID_LOGIN_ID, VALID_PW, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request),
                    new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {});
        }

        @DisplayName("정상 정보로 비밀번호 변경 시 200 OK 응답을 받는다.")
        @Test
        void returnsOk_whenValidRequest() {
            // arrange
            signUpDefault();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", VALID_LOGIN_ID);
            headers.set("X-Loopers-LoginPw", VALID_PW);
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(VALID_PW, "newPassword456");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("현재 비밀번호와 새 비밀번호가 같으면 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNewPasswordSameAsCurrent() {
            // arrange
            signUpDefault();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", VALID_LOGIN_ID);
            headers.set("X-Loopers-LoginPw", VALID_PW);
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(VALID_PW, VALID_PW);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 RULE 위반(7자) 이면 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNewPasswordTooShort() {
            // arrange
            signUpDefault();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", VALID_LOGIN_ID);
            headers.set("X-Loopers-LoginPw", VALID_PW);
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(VALID_PW, "short1!");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("X-Loopers 헤더가 없으면 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenHeadersMissing() {
            // arrange
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(VALID_PW, "newPassword456");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
