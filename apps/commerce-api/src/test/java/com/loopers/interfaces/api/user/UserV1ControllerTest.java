package com.loopers.interfaces.api.user;

import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import fixture.UserModelFixture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ControllerTest {

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final BCryptPasswordEncoder passwordEncoder;

    private final String REQUEST_BASE_URL = "/api/v1/users";

    @Autowired
    public UserV1ControllerTest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        DatabaseCleanUp databaseCleanUp,
        BCryptPasswordEncoder passwordEncoder
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.passwordEncoder = passwordEncoder;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/users/{id}")
    @Nested
    class Get {

        UserModel userModel = null;
        @BeforeEach
        void init() {
            UserModel target = UserModelFixture.defaults().toModel();
            target.changePassword(passwordEncoder.encode(target.getPassword()));
            userModel = userJpaRepository.save(target);

        }

        @DisplayName("존재하는 유저 ID를 주면, 해당 유저 정보를 반환한다.")
        @Test
        void returnsUsers_whenValidIdIsProvided() {
            // given
            String requestUrl = REQUEST_BASE_URL + "/" + userModel.getId();

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(null), responseType);

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo(userModel.getLoginId())
            );
        }

        @DisplayName("존재하지 않는 유저 ID를 주면, 404 NOT_FOUND응답을 받는다")
        @Test
        void throwsUsers_whenInValidIdIsProvided() {
            // given
            long invalidId = 999_999L;
            String requestUrl = REQUEST_BASE_URL + "/" + invalidId;

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(null), responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is4xxClientError()),
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }

        @DisplayName("숫자가 아닌 유저 ID를 주면, 400 BAD_REQUEST응답을 받는다")
        @Test
        void throwsBadRequest_whenInValidIdIsProvided() {
            // given
            String invalidId = "999_999";
            String requestUrl = REQUEST_BASE_URL + "/" + invalidId;

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(null), responseType);

            // then
            assertAll(
                    () -> assertTrue(response.getStatusCode().is4xxClientError()),
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    class Post {

        @DisplayName("유효한 입력값이 들어오면, 해당 유저 정보를 반환한다.")
        @Test
        public void returnUserResponse_whenValidInputProvided() {
            // given
            UserV1Dto.CreateUserRequest request = new UserV1Dto.CreateUserRequest(
                "tester",
                "테스터",
                LocalDate.of(1993, 3, 16),
                "q1w2e3r4!",
                "test@test.com"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<UserV1Dto.CreateUserRequest> httpEntity = new HttpEntity<>(request, headers);

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange(REQUEST_BASE_URL, HttpMethod.POST, httpEntity, responseType);

            // then
            assertAll(
                () -> assertEquals(HttpStatus.OK, response.getStatusCode()),
                () -> assertNotNull(response.getBody()),
                () -> assertEquals("tester", response.getBody().data().loginId())
            );
        }

        @DisplayName("유효하지 않은 입력값이 들어오면, 400 Bad Request를 반환한다.")
        @ParameterizedTest(name = "{1}")
        @MethodSource("com.loopers.interfaces.api.user.UserV1ControllerTest#invalidInputsForPost")
        void throwsBadRequest_whenInvalidInputProvided(UserV1Dto.CreateUserRequest request, String description) {
            // given
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<UserV1Dto.CreateUserRequest> httpEntity = new HttpEntity<>(request, headers);

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange(REQUEST_BASE_URL, HttpMethod.POST, httpEntity, responseType);

            // then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }
    }

    @DisplayName("PATCH /api/v1/users/{id}")
    @Nested
    class Patch {

        long id = 0L;
        @BeforeEach
        void init() {
            UserModel target = UserModelFixture.defaults().toModel();
            target.changePassword(passwordEncoder.encode(target.getPassword()));
            id = userJpaRepository.save(target).getId();
        }

        @DisplayName("유효한 입력값이 들어오면, OK에 body는 null 인 상태를 반환한다.")
        @Test
        public void returnNon_whenValidInputProvided() {
            // given
            UserV1Dto.ChangeUserPasswordRequest request = new UserV1Dto.ChangeUserPasswordRequest(
                    UserModelFixture.defaults().password(),
                    "target_1234"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<UserV1Dto.ChangeUserPasswordRequest> httpEntity = new HttpEntity<>(request, headers);

            // when
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(REQUEST_BASE_URL + "/" + id, HttpMethod.PATCH, httpEntity, responseType);

            // then
            assertAll(
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode())
            );
        }

        @DisplayName("유효하지 않은 입력값이 들어오면, 400 Bad Request를 반환한다.")
        @ParameterizedTest(name = "{1}")
        @MethodSource("com.loopers.interfaces.api.user.UserV1ControllerTest#invalidInputsForPatch")
        void throwsBadRequest_whenInvalidInputProvided(UserV1Dto.ChangeUserPasswordRequest request, String description) {
            // given
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<UserV1Dto.ChangeUserPasswordRequest> httpEntity = new HttpEntity<>(request, headers);

            // when
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(REQUEST_BASE_URL + "/" + id, HttpMethod.PATCH, httpEntity, responseType);

            // then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @DisplayName("존재하지 않는 유저 ID를 주면, 404 NOT_FOUND를 호출한다.")
        @Test
        void throwsBadRequest_whenIdEmpty() {
            // given
            Long id = 999_999L;
            UserV1Dto.ChangeUserPasswordRequest request = new UserV1Dto.ChangeUserPasswordRequest(
                    "original_1234",
                    "target_1234"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<UserV1Dto.ChangeUserPasswordRequest> httpEntity = new HttpEntity<>(request, headers);

            // when
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(REQUEST_BASE_URL + "/" + id, HttpMethod.PATCH, httpEntity, responseType);

            // then
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }
    }

    static Stream<Arguments> invalidInputsForPost() {
        LocalDate validBirth = LocalDate.of(1993, 3, 16);
        return Stream.of(
            Arguments.of(new UserV1Dto.CreateUserRequest("", "테스터", validBirth, "q1w2e3r4!", "test@test.com"), "loginId 공백"),
            Arguments.of(new UserV1Dto.CreateUserRequest("tester", "", validBirth, "q1w2e3r4!", "test@test.com"), "name 공백"),
            Arguments.of(new UserV1Dto.CreateUserRequest("tester", "테스터", validBirth, "short", "test@test.com"), "비밀번호 8자 미만"),
            Arguments.of(new UserV1Dto.CreateUserRequest("tester", "테스터", validBirth, "q1w2e3r4!@#$%^&*(", "test@test.com"), "비밀번호 16자 초과"),
            Arguments.of(new UserV1Dto.CreateUserRequest("tester", "테스터", validBirth, "q1w2e3r4!", "not-email"), "이메일 형식 오류")
        );
    }

    static Stream<Arguments> invalidInputsForPatch() {
        return Stream.of(
                Arguments.of(new UserV1Dto.ChangeUserPasswordRequest("orig_12", "target_1234"), "oldPassword 8자 미만"),
                Arguments.of(new UserV1Dto.ChangeUserPasswordRequest("original_1234", "targ_12"), "targetPassword 8자 미만"),
                Arguments.of(new UserV1Dto.ChangeUserPasswordRequest("original_12345678", "target_1234"), "oldPassword 16자 초과"),
                Arguments.of(new UserV1Dto.ChangeUserPasswordRequest("original_1234", "target_1234567890"), "targetPassword 16자 초과")
        );
    }
}
