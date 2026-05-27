package com.loopers.interfaces.api;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.user.UserDto;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserApiE2ETest {

    private static final String BASE_URL = "/api/v1/users";
    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PW_HEADER = "X-Loopers-LoginPw";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

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
            UserDto.SignUpRequest request = new UserDto.SignUpRequest(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            );

            // act
            ResponseEntity<ApiResponse<UserDto.SignUpResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response)
                .satisfies(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED))
                .satisfies(r -> assertThat(r.getBody().data().loginId()).isEqualTo("user01"));
        }

        @DisplayName("중복된 loginId로 가입하면, 409 응답을 반환한다.")
        @Test
        void returns409_whenLoginIdIsDuplicated() {
            // arrange
            userRepository.save(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            UserDto.SignUpRequest request = new UserDto.SignUpRequest(
                "user01", "Password2@", "김철수",
                LocalDate.of(1995, 5, 5), "other@example.com"
            );

            // act
            ResponseEntity<ApiResponse<UserDto.SignUpResponse>> response = testRestTemplate.exchange(
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
            UserDto.SignUpRequest request = new UserDto.SignUpRequest(
                null, "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            );

            // act
            ResponseEntity<ApiResponse<UserDto.SignUpResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("loginId가 빈 문자열이면, 400 응답을 반환한다.")
        @Test
        void returns400_whenLoginIdIsBlank() {
            // arrange
            UserDto.SignUpRequest request = new UserDto.SignUpRequest(
                "", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            );

            // act
            ResponseEntity<ApiResponse<UserDto.SignUpResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("password가 null이면, 400 응답을 반환한다.")
        @Test
        void returns400_whenPasswordIsNull() {
            // arrange
            UserDto.SignUpRequest request = new UserDto.SignUpRequest(
                "user01", null, "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            );

            // act
            ResponseEntity<ApiResponse<UserDto.SignUpResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("이메일 형식이 잘못되면, 400 응답을 반환한다.")
        @Test
        void returns400_whenEmailFormatIsInvalid() {
            // arrange
            UserDto.SignUpRequest request = new UserDto.SignUpRequest(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "invalid-email"
            );

            // act
            ResponseEntity<ApiResponse<UserDto.SignUpResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("중복된 email로 가입하면, 409 응답을 반환한다.")
        @Test
        void returns409_whenEmailIsDuplicated() {
            // arrange
            userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            UserDto.SignUpRequest request = new UserDto.SignUpRequest(
                "user02", "Password2@", "김철수",
                LocalDate.of(1995, 5, 5), "user@example.com"
            );

            // act
            ResponseEntity<ApiResponse<UserDto.SignUpResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("탈퇴한 계정의 email로 가입하면, 201 응답을 반환한다.")
        @Test
        void returns201_whenEmailIsDuplicatedWithWithdrawnAccount() {
            // arrange
            userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.set(LOGIN_ID_HEADER, "user01");
            headers.set(LOGIN_PW_HEADER, "Password1!");

            testRestTemplate.exchange(
                BASE_URL + "/me", HttpMethod.DELETE,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            UserDto.SignUpRequest request = new UserDto.SignUpRequest(
                "user02", "Password2@", "김철수",
                LocalDate.of(1995, 5, 5), "user@example.com"
            );

            // act
            ResponseEntity<ApiResponse<UserDto.SignUpResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetUser {

        @DisplayName("유효한 헤더로 요청하면, 유저 정보(이름 마스킹)를 반환한다.")
        @Test
        void returnsUserInfo_whenValidHeadersAreProvided() {
            // arrange
            userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.set(LOGIN_ID_HEADER, "user01");
            headers.set(LOGIN_PW_HEADER, "Password1!");

            // act
            ResponseEntity<ApiResponse<UserDto.UserResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/me", HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response)
                .satisfies(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK))
                .satisfies(r -> assertThat(r.getBody().data().loginId()).isEqualTo("user01"))
                .satisfies(r -> assertThat(r.getBody().data().name()).isEqualTo("홍길*"))
                .satisfies(r -> assertThat(r.getBody().data().email()).isEqualTo("user@example.com"));
        }

        @DisplayName("헤더가 없으면, 401 응답을 반환한다.")
        @Test
        void returns401_whenHeadersAreMissing() {
            // act
            ResponseEntity<ApiResponse<UserDto.UserResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/me", HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("loginId 헤더가 공백이면, 401 응답을 반환한다.")
        @Test
        void returns401_whenLoginIdHeaderIsBlank() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set(LOGIN_ID_HEADER, "   ");
            headers.set(LOGIN_PW_HEADER, "Password1!");

            // act
            ResponseEntity<ApiResponse<UserDto.UserResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/me", HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("password 헤더가 공백이면, 401 응답을 반환한다.")
        @Test
        void returns401_whenPasswordHeaderIsBlank() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set(LOGIN_ID_HEADER, "user01");
            headers.set(LOGIN_PW_HEADER, "   ");

            // act
            ResponseEntity<ApiResponse<UserDto.UserResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/me", HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("loginId 헤더가 빈 문자열이면, 401 응답을 반환한다.")
        @Test
        void returns401_whenLoginIdHeaderIsEmpty() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set(LOGIN_ID_HEADER, "");
            headers.set(LOGIN_PW_HEADER, "Password1!");

            // act
            ResponseEntity<ApiResponse<UserDto.UserResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/me", HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 틀리면, 401 응답을 반환한다.")
        @Test
        void returns401_whenPasswordIsWrong() {
            // arrange
            userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.set(LOGIN_ID_HEADER, "user01");
            headers.set(LOGIN_PW_HEADER, "WrongPassword!");

            // act
            ResponseEntity<ApiResponse<UserDto.UserResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/me", HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PUT /api/v1/users/password")
    @Nested
    class UpdatePassword {

        @DisplayName("유효한 요청이면, 200 응답을 반환한다.")
        @Test
        void returns200_whenPasswordUpdateSucceeds() {
            // arrange
            userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.set(LOGIN_ID_HEADER, "user01");
            headers.set(LOGIN_PW_HEADER, "Password1!");

            UserDto.UpdatePasswordRequest request = new UserDto.UpdatePasswordRequest(
                "Password1!", "NewPassword1!"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/password", HttpMethod.PUT,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("기존 비밀번호가 틀리면, 400 응답을 반환한다.")
        @Test
        void returns400_whenOldPasswordIsWrong() {
            // arrange
            userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.set(LOGIN_ID_HEADER, "user01");
            headers.set(LOGIN_PW_HEADER, "Password1!");

            UserDto.UpdatePasswordRequest request = new UserDto.UpdatePasswordRequest(
                "WrongPassword!", "NewPassword1!"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/password", HttpMethod.PUT,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, 400 응답을 반환한다.")
        @Test
        void returns400_whenNewPasswordIsSameAsCurrent() {
            // arrange
            userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.set(LOGIN_ID_HEADER, "user01");
            headers.set(LOGIN_PW_HEADER, "Password1!");

            UserDto.UpdatePasswordRequest request = new UserDto.UpdatePasswordRequest(
                "Password1!", "Password1!"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/password", HttpMethod.PUT,
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
            UserDto.UpdatePasswordRequest request = new UserDto.UpdatePasswordRequest(
                "Password1!", "NewPassword1!"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/password", HttpMethod.PUT,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("비밀번호 헤더가 틀리면, 401 응답을 반환한다.")
        @Test
        void returns401_whenPasswordHeaderIsWrong() {
            // arrange
            userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.set(LOGIN_ID_HEADER, "user01");
            headers.set(LOGIN_PW_HEADER, "WrongPassword!");

            UserDto.UpdatePasswordRequest request = new UserDto.UpdatePasswordRequest(
                "Password1!", "NewPassword1!"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/password", HttpMethod.PUT,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("DELETE /api/v1/users/me")
    @Nested
    class DeleteUser {

        @DisplayName("유효한 요청이면, 200 응답을 반환한다.")
        @Test
        void returns200_whenWithdrawSucceeds() {
            // arrange
            userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.set(LOGIN_ID_HEADER, "user01");
            headers.set(LOGIN_PW_HEADER, "Password1!");

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/me", HttpMethod.DELETE,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("헤더가 없으면, 401 응답을 반환한다.")
        @Test
        void returns401_whenHeadersAreMissing() {
            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                BASE_URL + "/me", HttpMethod.DELETE,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("탈퇴 후 동일 loginId로 재가입하면, 201 응답을 반환한다.")
        @Test
        void returns201_whenSignUpWithSameLoginIdAfterWithdraw() {
            // arrange
            userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.set(LOGIN_ID_HEADER, "user01");
            headers.set(LOGIN_PW_HEADER, "Password1!");

            testRestTemplate.exchange(
                BASE_URL + "/me", HttpMethod.DELETE,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            UserDto.SignUpRequest request = new UserDto.SignUpRequest(
                "user01", "Password2@", "김철수",
                LocalDate.of(1995, 5, 5), "other@example.com"
            );

            // act
            ResponseEntity<ApiResponse<UserDto.SignUpResponse>> response = testRestTemplate.exchange(
                BASE_URL, HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @DisplayName("탈퇴 후 GET /api/v1/users/me 요청 시, 401 응답을 반환한다.")
        @Test
        void returns401_whenAccessingMeAfterWithdraw() {
            // arrange
            userService.signUp(new UserModel(
                "user01", "Password1!", "홍길동",
                LocalDate.of(1990, 1, 1), "user@example.com"
            ));

            HttpHeaders headers = new HttpHeaders();
            headers.set(LOGIN_ID_HEADER, "user01");
            headers.set(LOGIN_PW_HEADER, "Password1!");

            testRestTemplate.exchange(
                BASE_URL + "/me", HttpMethod.DELETE,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // act
            ResponseEntity<ApiResponse<UserDto.UserResponse>> response = testRestTemplate.exchange(
                BASE_URL + "/me", HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
