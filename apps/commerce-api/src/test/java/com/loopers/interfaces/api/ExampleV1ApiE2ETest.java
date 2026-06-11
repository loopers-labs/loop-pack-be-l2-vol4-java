package com.loopers.interfaces.api;

import com.loopers.domain.example.Example;
import com.loopers.domain.user.UserService;
import com.loopers.domain.user.command.SignUpUserCommand;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.LoginId;
import com.loopers.domain.user.vo.PlainPassword;
import com.loopers.domain.user.vo.UserName;
import com.loopers.infrastructure.example.ExampleJpaRepository;
import com.loopers.interfaces.api.example.ExampleV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDate;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ExampleV1ApiE2ETest {

    private static final Function<Long, String> ENDPOINT_GET = id -> "/api/v1/examples/" + id;
    private static final String AUTH_LOGIN_ID = "loopers01";
    private static final String AUTH_PASSWORD = "Loopers!2026";

    private final TestRestTemplate testRestTemplate;
    private final ExampleJpaRepository exampleJpaRepository;
    private final UserService userService;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ExampleV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        ExampleJpaRepository exampleJpaRepository,
        UserService userService,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.exampleJpaRepository = exampleJpaRepository;
        this.userService = userService;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        BirthDate birthDate = BirthDate.of(LocalDate.of(1993, 11, 3));
        SignUpUserCommand signUpUserCommand = new SignUpUserCommand(
            LoginId.of(AUTH_LOGIN_ID),
            PlainPassword.of(AUTH_PASSWORD, birthDate),
            UserName.of("김성호"),
            birthDate,
            Email.of("loopers@example.com")
        );
        userService.signUp(signUpUserCommand);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpEntity<Void> authEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", AUTH_LOGIN_ID);
        headers.set("X-Loopers-LoginPw", AUTH_PASSWORD);
        return new HttpEntity<>(headers);
    }

    @DisplayName("GET /api/v1/examples/{id}")
    @Nested
    class Get {
        @DisplayName("존재하는 예시 ID를 주면, 해당 예시 정보를 반환한다.")
        @Test
        void returnsExampleInfo_whenValidIdIsProvided() {
            // arrange
            Example exampleModel = exampleJpaRepository.save(
                Example.builder().name("예시 제목").description("예시 설명").build()
            );
            String requestUrl = ENDPOINT_GET.apply(exampleModel.getId());

            // act
            ParameterizedTypeReference<ApiResponse<ExampleV1Dto.ExampleResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ExampleV1Dto.ExampleResponse>> response =
                testRestTemplate.exchange(requestUrl, HttpMethod.GET, authEntity(), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(exampleModel.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo(exampleModel.getName()),
                () -> assertThat(response.getBody().data().description()).isEqualTo(exampleModel.getDescription())
            );
        }

        @DisplayName("숫자가 아닌 ID 로 요청하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenIdIsNotProvided() {
            // arrange
            String requestUrl = "/api/v1/examples/나나";

            // act
            ParameterizedTypeReference<ApiResponse<ExampleV1Dto.ExampleResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ExampleV1Dto.ExampleResponse>> response =
                testRestTemplate.exchange(requestUrl, HttpMethod.GET, authEntity(), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }

        @DisplayName("존재하지 않는 예시 ID를 주면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void throwsException_whenInvalidIdIsProvided() {
            // arrange
            Long invalidId = -1L;
            String requestUrl = ENDPOINT_GET.apply(invalidId);

            // act
            ParameterizedTypeReference<ApiResponse<ExampleV1Dto.ExampleResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ExampleV1Dto.ExampleResponse>> response =
                testRestTemplate.exchange(requestUrl, HttpMethod.GET, authEntity(), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }
    }
}
