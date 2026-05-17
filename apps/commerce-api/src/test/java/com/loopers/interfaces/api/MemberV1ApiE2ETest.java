package com.loopers.interfaces.api;

import com.loopers.interfaces.api.member.MemberV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MemberV1ApiE2ETest {

    private static final String MEMBER_V1_PATH = "/api/v1/members";

    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private TestRestTemplate testRestTemplate;

    public String loginId;
    public String loginPassword;
    public String name ;
    public LocalDate birthday;
    public String email;


    @BeforeEach
    public void setUp() {
        loginId = "loopers";
        loginPassword = "pAssWord1!";
        name = "루퍼스";
        birthday = LocalDate.parse("2000-01-01");
        email = "email@email.com";
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/members")
    @Nested
    class PostMemberV1ApiE2E {
        @DisplayName("회원 정보가 모두 주어지면, 정상적으로 회원가입이 된다.")
        @Test
        void saveAndReturnMemberInfo_whenValidInfoProvided(){

            // arrange
            MemberV1Dto.MemberJoinRequest request = new MemberV1Dto.MemberJoinRequest(loginId, loginPassword, name, birthday, email);
            // act
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(MEMBER_V1_PATH,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {});

            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo(loginId),
                    () -> assertThat(response.getBody().data().email()).isEqualTo(email),
                    () -> assertThat(response.getBody().data().birthday()).isEqualTo(birthday)
            );

        }

        @DisplayName("이미 사용 중인 로그인 ID면, 409를 반환한다.")
        @Test
        void returnConflict_whenLoginIdAlreadyExists() {
            // arrange
            MemberV1Dto.MemberJoinRequest firstRequest = new MemberV1Dto.MemberJoinRequest(loginId, loginPassword, name, birthday, email);
            testRestTemplate.exchange(MEMBER_V1_PATH, HttpMethod.POST, new HttpEntity<>(firstRequest), new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {});

            MemberV1Dto.MemberJoinRequest duplicateRequest = new MemberV1Dto.MemberJoinRequest(loginId, loginPassword, "홍길동", LocalDate.of(2000, 5, 5), "new@email.com");

            // act
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(MEMBER_V1_PATH, HttpMethod.POST, new HttpEntity<>(duplicateRequest), new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode().value()).isEqualTo(409);
        }

        @DisplayName("비밀번호가 형식에 맞지 않으면, 400을 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = {"       ", "1234", "안녕하세요반갑습니다", "abcdefghijklmnopqrstuvwxyz"})
        void returnBadRequest_whenLoginPasswordIsInvalid(String invalidPassword) {
            // arrange
            MemberV1Dto.MemberJoinRequest request = new MemberV1Dto.MemberJoinRequest(loginId, invalidPassword, name, birthday, email);

            // act
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(MEMBER_V1_PATH, HttpMethod.POST, new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }

        @DisplayName("비밀번호에 생년월일이 포함되어 있으면, 400을 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = {"Pass!20000101", "Pass!0101"})
        void returnBadRequest_whenLoginPasswordContainsBirthday(String passwordWithBirthday) {
            // arrange
            MemberV1Dto.MemberJoinRequest request = new MemberV1Dto.MemberJoinRequest(loginId, passwordWithBirthday, name, birthday, email);

            // act
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(MEMBER_V1_PATH, HttpMethod.POST, new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }
    }

    @DisplayName("GET /api/v1/members/info")
    @Nested
    class GetMemberV1ApiE2E {

        @DisplayName("올바른 헤더가 주어지면, 회원 정보를 반환한다.")
        @Test
        void returnMemberInfo_whenValidHeadersProvided() {
            // arrange
            testRestTemplate.exchange(MEMBER_V1_PATH, HttpMethod.POST,
                    new HttpEntity<>(new MemberV1Dto.MemberJoinRequest(loginId, loginPassword, name, birthday, email)),
                    new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {});

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", loginId);
            headers.set("X-Loopers-LoginPw", loginPassword);

            // act
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                    MEMBER_V1_PATH + "/info", HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo(loginId),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("루퍼*"),
                    () -> assertThat(response.getBody().data().email()).isEqualTo(email),
                    () -> assertThat(response.getBody().data().birthday()).isEqualTo(birthday)
            );
        }

        @DisplayName("X-Loopers-LoginId 헤더가 없으면, 401을 반환한다.")
        @Test
        void returnUnauthorized_whenLoginIdHeaderIsMissing() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginPw", loginPassword);

            // act
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                    MEMBER_V1_PATH + "/info", HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode().value()).isEqualTo(401);
        }

        @DisplayName("X-Loopers-LoginPw 헤더가 없으면, 401을 반환한다.")
        @Test
        void returnUnauthorized_whenLoginPwHeaderIsMissing() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", loginId);

            // act
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                    MEMBER_V1_PATH + "/info", HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode().value()).isEqualTo(401);
        }

        @DisplayName("존재하지 않는 로그인 ID면, 401을 반환한다.")
        @Test
        void returnUnauthorized_whenLoginIdDoesNotExist() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", loginId);
            headers.set("X-Loopers-LoginPw", loginPassword);

            // act
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                    MEMBER_V1_PATH + "/info", HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode().value()).isEqualTo(401);
        }

        @DisplayName("비밀번호가 올바르지 않으면, 401을 반환한다.")
        @Test
        void returnUnauthorized_whenLoginPasswordDoesNotMatch() {
            // arrange
            testRestTemplate.exchange(MEMBER_V1_PATH, HttpMethod.POST,
                    new HttpEntity<>(new MemberV1Dto.MemberJoinRequest(loginId, loginPassword, name, birthday, email)),
                    new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {});

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", loginId);
            headers.set("X-Loopers-LoginPw", "wrongPass1!");

            // act
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                    MEMBER_V1_PATH + "/info", HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode().value()).isEqualTo(401);
        }
    }

    @DisplayName("PUT /api/v1/members/change-password")
    @Nested
    class PutChangePasswordV1ApiE2E {

        private HttpHeaders authHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", loginId);
            headers.set("X-Loopers-LoginPw", loginPassword);
            return headers;
        }

        private void join() {
            testRestTemplate.exchange(MEMBER_V1_PATH, HttpMethod.POST,
                    new HttpEntity<>(new MemberV1Dto.MemberJoinRequest(loginId, loginPassword, name, birthday, email)),
                    new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {});
        }

        @DisplayName("유효한 새 비밀번호가 주어지면, 정상적으로 비밀번호 변경이 되고 새 비밀번호로 회원 조회가 된다.")
        @Test
        void returnOk_whenNewPasswordIsValid() {
            // arrange
            join();
            String newPassword = "pAssWord2!";
            MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest(loginPassword, newPassword);

            // act
            ResponseEntity<ApiResponse<Void>> changeResponse = testRestTemplate.exchange(
                    MEMBER_V1_PATH + "/change-password", HttpMethod.PUT,
                    new HttpEntity<>(request, authHeaders()), new ParameterizedTypeReference<>() {});

            HttpHeaders newHeaders = new HttpHeaders();
            newHeaders.set("X-Loopers-LoginId", loginId);
            newHeaders.set("X-Loopers-LoginPw", newPassword);
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> infoResponse = testRestTemplate.exchange(
                    MEMBER_V1_PATH + "/info", HttpMethod.GET,
                    new HttpEntity<>(newHeaders), new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertTrue(changeResponse.getStatusCode().is2xxSuccessful()),
                    () -> assertTrue(infoResponse.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(infoResponse.getBody().data().loginId()).isEqualTo(loginId)
            );
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면, 400을 반환한다.")
        @Test
        void returnBadRequest_whenNewPasswordEqualsCurrentPassword() {
            // arrange
            join();
            MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest(loginPassword, loginPassword);

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    MEMBER_V1_PATH + "/change-password", HttpMethod.PUT,
                    new HttpEntity<>(request, authHeaders()), new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }

        @DisplayName("새 비밀번호가 형식에 맞지 않으면, 400을 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = {"       ", "1234", "안녕하세요반갑습니다", "abcdefghijklmnopqrstuvwxyz", "Pass!20000101", "Pass!0101"})
        void returnBadRequest_whenNewPasswordIsInvalid(String invalidPassword) {
            // arrange
            join();
            MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest(loginPassword, invalidPassword);

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    MEMBER_V1_PATH + "/change-password", HttpMethod.PUT,
                    new HttpEntity<>(request, authHeaders()), new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode().value()).isEqualTo(400);
        }
    }

}
