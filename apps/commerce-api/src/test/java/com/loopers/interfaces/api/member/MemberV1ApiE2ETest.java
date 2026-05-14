package com.loopers.interfaces.api.member;

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
import org.springframework.http.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberV1ApiE2ETest {

    private static final String ENDPOINT_SIGNUP = "/v1/members/signup";
    private static final String ENDPOINT_ME = "/v1/members/me";
    private static final String ENDPOINT_PASSWORD = "/v1/members/me/password";

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

    @DisplayName("POST /v1/members/signup")
    @Nested
    class SignUp {
        @DisplayName("올바른 회원가입 요청을 보내면, 200 OK 응답을 받는다.")
        @Test
        void returnsOk_whenValidRequestIsProvided() {
            // arrange
            MemberV1Dto.SignUpRequest signUpRequest = new MemberV1Dto.SignUpRequest(
                    "tester123",
                    "Password123!",
                    "테스터",
                    LocalDate.of(1990, 1, 1),
                    "tester@example.com"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_SIGNUP,
                    HttpMethod.POST,
                    new HttpEntity<>(signUpRequest),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK)
            );
        }
    }

    @DisplayName("GET /v1/members/me")
    @Nested
    class GetMyInfo {
        @DisplayName("올바른 인증 정보를 헤더에 담아 요청하면, 마스킹된 회원 정보를 반환한다.")
        @Test
        void returnsMaskedMemberInfo_whenValidCredentialsAreProvided() {
            // arrange
            signUp("tester123", "Password123!", "테스터");

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "tester123");
            headers.set("X-Loopers-LoginPw", "Password123!");

            // act
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ME,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo("tester123"),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("테스*")
            );
        }

        @DisplayName("잘못된 비밀번호로 요청하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void throwsUnauthorized_whenInvalidPasswordIsProvided() {
            // arrange
            signUp("tester123", "Password123!", "테스터");

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "tester123");
            headers.set("X-Loopers-LoginPw", "WrongPassword!");

            // act
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ME,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is4xxClientError()),
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }
    }

    @DisplayName("PATCH /v1/members/me/password")
    @Nested
    class UpdatePassword {
        @DisplayName("올바른 비밀번호 변경 요청을 보내면, 200 OK 응답을 받고 이후 새 비밀번호로 조회가 가능하다.")
        @Test
        void returnsOk_andEnablesLoginWithNewPassword() {
            // arrange
            signUp("tester123", "OldPassword123!", "테스터");

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "tester123");
            headers.set("X-Loopers-LoginPw", "OldPassword123!");

            MemberV1Dto.UpdatePasswordRequest updateRequest = new MemberV1Dto.UpdatePasswordRequest(
                    "OldPassword123!",
                    "NewPassword123!"
            );

            // act
            ResponseEntity<ApiResponse<Void>> updateResponse = testRestTemplate.exchange(
                    ENDPOINT_PASSWORD,
                    HttpMethod.PATCH,
                    new HttpEntity<>(updateRequest, headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // assert
            assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // verify change
            HttpHeaders newHeaders = new HttpHeaders();
            newHeaders.set("X-Loopers-LoginId", "tester123");
            newHeaders.set("X-Loopers-LoginPw", "NewPassword123!");

            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> getInfoResponse = testRestTemplate.exchange(
                    ENDPOINT_ME,
                    HttpMethod.GET,
                    new HttpEntity<>(newHeaders),
                    new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            );
            assertThat(getInfoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    private void signUp(String loginId, String password, String name) {
        MemberV1Dto.SignUpRequest request = new MemberV1Dto.SignUpRequest(
                loginId,
                password,
                name,
                LocalDate.of(1990, 1, 1),
                loginId + "@example.com"
        );
        testRestTemplate.postForEntity(ENDPOINT_SIGNUP, request, ApiResponse.class);
    }
}
