package com.loopers.interfaces.api;

import com.loopers.interfaces.api.member.MemberV1Dto;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api/v1/members";
    private static final String ENDPOINT_GET_ME = "/api/v1/members/me";
    private static final String ENDPOINT_CHANGE_PASSWORD = "/api/v1/members/me/password";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public MemberV1ApiE2ETest(
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

    @DisplayName("POST /api/v1/members")
    @Nested
    class Register {

        @DisplayName("올바른 정보가 주어지면, 201 Created 응답을 받는다.")
        @Test
        void returns201_whenAllFieldsAreValid() {
            // Arrange
            MemberV1Dto.RegisterRequest request = new MemberV1Dto.RegisterRequest(
                "testUser1", "Password1!", "홍길동", "1990-01-01", "test@example.com"
            );

            // Act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType
            );

            // Assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED)
            );
        }

        @DisplayName("이미 가입된 loginId로 요청하면, 409 CONFLICT 응답을 받는다.")
        @Test
        void returns409_whenLoginIdAlreadyExists() {
            // Arrange
            MemberV1Dto.RegisterRequest request = new MemberV1Dto.RegisterRequest(
                "testUser1", "Password1!", "홍길동", "1990-01-01", "test@example.com"
            );
            testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), Void.class);

            // Act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType
            );

            // Assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );
        }
    }

    @DisplayName("GET /api/v1/members/me")
    @Nested
    class GetMe {

        @DisplayName("올바른 헤더가 주어지면, 200 OK와 회원 정보를 반환한다.")
        @Test
        void returns200_whenHeadersAreValid() {
            // Arrange
            MemberV1Dto.RegisterRequest registerRequest = new MemberV1Dto.RegisterRequest(
                "testUser1", "Password1!", "홍길동", "1990-01-01", "test@example.com"
            );
            testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(registerRequest), Void.class);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testUser1");
            headers.set("X-Loopers-LoginPw", "Password1!");

            // Act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                ENDPOINT_GET_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType
            );

            // Assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("testUser1"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길동"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo("1990-01-01"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("존재하지 않는 loginId로 요청하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void returns404_whenLoginIdDoesNotExist() {
            // Arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "notExist");
            headers.set("X-Loopers-LoginPw", "Password1!");

            // Act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                ENDPOINT_GET_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("비밀번호가 틀리면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returns401_whenPasswordIsWrong() {
            // Arrange
            MemberV1Dto.RegisterRequest registerRequest = new MemberV1Dto.RegisterRequest(
                "testUser1", "Password1!", "홍길동", "1990-01-01", "test@example.com"
            );
            testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(registerRequest), Void.class);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testUser1");
            headers.set("X-Loopers-LoginPw", "WrongPassword1!");

            // Act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                ENDPOINT_GET_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PATCH /api/v1/members/me/password")
    @Nested
    class ChangePassword {

        @DisplayName("올바른 정보가 주어지면, 200 OK 응답을 받는다.")
        @Test
        void returns200_whenCredentialsAreValid() {
            // Arrange
            testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST,
                new HttpEntity<>(new MemberV1Dto.RegisterRequest("testUser1", "Password1!", "홍길동", "1990-01-01", "test@example.com")),
                Void.class);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testUser1");
            headers.set("X-Loopers-LoginPw", "Password1!");

            MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest("Password1!", "NewPassword2@");

            // Act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType
            );

            // Assert
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("기존 비밀번호가 틀리면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returns401_whenOldPasswordIsWrong() {
            // Arrange
            testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST,
                new HttpEntity<>(new MemberV1Dto.RegisterRequest("testUser1", "Password1!", "홍길동", "1990-01-01", "test@example.com")),
                Void.class);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testUser1");
            headers.set("X-Loopers-LoginPw", "Password1!");

            MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest("WrongPassword1!", "NewPassword2@");

            // Act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("헤더 비밀번호가 틀리면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returns401_whenLoginPwIsWrong() {
            // Arrange
            testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST,
                new HttpEntity<>(new MemberV1Dto.RegisterRequest("testUser1", "Password1!", "홍길동", "1990-01-01", "test@example.com")),
                Void.class);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testUser1");
            headers.set("X-Loopers-LoginPw", "WrongPassword1!"); // 헤더 비번 틀림

            MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest("Password1!", "NewPassword2@");

            // Act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returns400_whenNewPasswordIsSameAsCurrent() {
            // Arrange
            testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST,
                new HttpEntity<>(new MemberV1Dto.RegisterRequest("testUser1", "Password1!", "홍길동", "1990-01-01", "test@example.com")),
                Void.class);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testUser1");
            headers.set("X-Loopers-LoginPw", "Password1!");

            MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest("Password1!", "Password1!");

            // Act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
