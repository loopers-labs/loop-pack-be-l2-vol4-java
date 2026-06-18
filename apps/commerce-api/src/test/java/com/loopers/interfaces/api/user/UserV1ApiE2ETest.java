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
import org.springframework.http.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_SIGNUP = "/v1/users/signup";
    private static final String ENDPOINT_ME = "/v1/users/me";
    private static final String ENDPOINT_PASSWORD = "/v1/users/me/password";

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

    @DisplayName("POST /v1/users/signup")
    @Nested
    class SignUp {
        @DisplayName("?щ컮瑜??뚯썝媛???붿껌??蹂대궡硫? 200 OK ?묐떟??諛쏅뒗??")
        @Test
        void returnsOk_whenValidRequestIsProvided() {
            // arrange
            UserV1Dto.SignUpRequest signUpRequest = new UserV1Dto.SignUpRequest(
                    "tester123",
                    "Password123!",
                    "?뚯뒪??,
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

    @DisplayName("GET /v1/users/me")
    @Nested
    class GetMyInfo {
        @DisplayName("?щ컮瑜??몄쬆 ?뺣낫瑜??ㅻ뜑???댁븘 ?붿껌?섎㈃, 留덉뒪?밸맂 ?뚯썝 ?뺣낫瑜?諛섑솚?쒕떎.")
        @Test
        void returnsMaskedUserInfo_whenValidCredentialsAreProvided() {
            // arrange
            signUp("tester123", "Password123!", "?뚯뒪??);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "tester123");
            headers.set("X-Loopers-LoginPw", "Password123!");

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ME,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo("tester123"),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("?뚯뒪*")
            );
        }

        @DisplayName("?섎せ??鍮꾨?踰덊샇濡??붿껌?섎㈃, 401 UNAUTHORIZED ?묐떟??諛쏅뒗??")
        @Test
        void throwsUnauthorized_whenInvalidPasswordIsProvided() {
            // arrange
            signUp("tester123", "Password123!", "?뚯뒪??);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "tester123");
            headers.set("X-Loopers-LoginPw", "WrongPassword!");

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ME,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is4xxClientError()),
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }
    }

    @DisplayName("PATCH /v1/users/me/password")
    @Nested
    class UpdatePassword {
        @DisplayName("?щ컮瑜?鍮꾨?踰덊샇 蹂寃??붿껌??蹂대궡硫? 200 OK ?묐떟??諛쏄퀬 ?댄썑 ??鍮꾨?踰덊샇濡?議고쉶媛 媛?ν븯??")
        @Test
        void returnsOk_andEnablesLoginWithNewPassword() {
            // arrange
            signUp("tester123", "OldPassword123!", "?뚯뒪??);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "tester123");
            headers.set("X-Loopers-LoginPw", "OldPassword123!");

            UserV1Dto.UpdatePasswordRequest updateRequest = new UserV1Dto.UpdatePasswordRequest(
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

            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> getInfoResponse = testRestTemplate.exchange(
                    ENDPOINT_ME,
                    HttpMethod.GET,
                    new HttpEntity<>(newHeaders),
                    new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
            );
            assertThat(getInfoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    private void signUp(String loginId, String password, String name) {
        UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                loginId,
                password,
                name,
                LocalDate.of(1990, 1, 1),
                loginId + "@example.com"
        );
        testRestTemplate.postForEntity(ENDPOINT_SIGNUP, request, ApiResponse.class);
    }
}
