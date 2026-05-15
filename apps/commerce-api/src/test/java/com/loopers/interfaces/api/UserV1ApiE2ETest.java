package com.loopers.interfaces.api;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
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
import org.springframework.http.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String BASE_URL = "/api/v1/users";
    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    class SignUp {

        @DisplayName("유효한 정보로 회원가입하면, 201 응답을 반환한다.")
        @Test
        void returns201_whenSignUpSucceeds() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            );

            // act
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("user01")
            );
        }

        @DisplayName("중복된 loginId로 가입하면, 409 응답을 반환한다.")
        @Test
        void returns409_whenLoginIdIsDuplicated() {
            // arrange
            userRepository.save(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "user01", "Password2@", "김철수",
                LocalDate.of(1995, 5, 5), "other@example.com"
            );

            // act
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("필수 필드가 없으면, 400 응답을 반환한다.")
        @Test
        void returns400_whenRequiredFieldIsMissing() {
            // arrange - loginId 없이 요청
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                null, "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            );

            // act
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetUser {

        @DisplayName("유효한 헤더로 요청하면, 유저 정보(이름 마스킹)를 반환한다.")
        @Test
        void returnsUserInfo_whenValidHeadersAreProvided() {
            // arrange
            userRepository.save(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.set(LOGIN_ID_HEADER, "user01");
            headers.set(LOGIN_PW_HEADER, "Password1!");

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/me", HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("user01"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("user@example.com")
            );
        }

        @DisplayName("헤더가 없으면, 401 응답을 반환한다.")
        @Test
        void returns401_whenHeadersAreMissing() {
            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/me", HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 틀리면, 401 응답을 반환한다.")
        @Test
        void returns401_whenPasswordIsWrong() {
            // arrange
            userRepository.save(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.set(LOGIN_ID_HEADER, "user01");
            headers.set(LOGIN_PW_HEADER, "WrongPassword!");

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/me", HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password")
    @Nested
    class UpdatePassword {

        @DisplayName("유효한 요청이면, 200 응답을 반환한다.")
        @Test
        void returns200_whenPasswordUpdateSucceeds() {
            // arrange
            userRepository.save(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.set(LOGIN_ID_HEADER, "user01");
            headers.set(LOGIN_PW_HEADER, "Password1!");

            UserV1Dto.UpdatePasswordRequest request = new UserV1Dto.UpdatePasswordRequest(
                "Password1!", "NewPassword1!"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/me/password", HttpMethod.PATCH,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("기존 비밀번호가 틀리면, 400 응답을 반환한다.")
        @Test
        void returns400_whenOldPasswordIsWrong() {
            // arrange
            userRepository.save(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.set(LOGIN_ID_HEADER, "user01");
            headers.set(LOGIN_PW_HEADER, "Password1!");

            UserV1Dto.UpdatePasswordRequest request = new UserV1Dto.UpdatePasswordRequest(
                "WrongPassword!", "NewPassword1!"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/me/password", HttpMethod.PATCH,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("헤더가 없으면, 401 응답을 반환한다.")
        @Test
        void returns401_whenHeadersAreMissing() {
            // arrange
            UserV1Dto.UpdatePasswordRequest request = new UserV1Dto.UpdatePasswordRequest(
                "Password1!", "NewPassword1!"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/me/password", HttpMethod.PATCH,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
