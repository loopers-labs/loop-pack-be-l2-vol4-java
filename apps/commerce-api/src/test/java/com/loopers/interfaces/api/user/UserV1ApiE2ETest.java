package com.loopers.interfaces.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.dto.UserResponse;
import com.loopers.utils.DatabaseCleanUp;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

  private static final String ENDPOINT_SIGN_UP = "/api/v1/users";

  private final TestRestTemplate testRestTemplate;
  private final DatabaseCleanUp databaseCleanUp;

  @Autowired
  UserV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
    this.testRestTemplate = testRestTemplate;
    this.databaseCleanUp = databaseCleanUp;
  }

  @AfterEach
  void tearDown() {
    databaseCleanUp.truncateAllTables();
  }

  @DisplayName("POST /api/v1/users")
  @Nested
  class SignUp {

    @DisplayName("회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다.")
    @Test
    void returnsCreatedUserInfo_whenSignUpSucceeds() {
      // arrange
      Map<String, Object> request =
          Map.of(
              "loginId", "loopers01",
              "password", "Password1!",
              "name", "홍길동",
              "birthDate", "1995-05-15",
              "email", "loopers@example.com",
              "gender", "MALE");

      // act
      ParameterizedTypeReference<ApiResponse<UserResponse>> responseType =
          new ParameterizedTypeReference<>() {};
      ResponseEntity<ApiResponse<UserResponse>> response =
          testRestTemplate.exchange(
              ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request), responseType);

      // assert
      assertAll(
          () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
          () -> assertThat(response.getBody()).isNotNull(),
          () -> assertThat(response.getBody().data()).isNotNull(),
          () -> assertThat(response.getBody().data().id()).isNotNull(),
          () -> assertThat(response.getBody().data().loginId()).isEqualTo(request.get("loginId")),
          () -> assertThat(response.getBody().data().name()).isEqualTo(request.get("name")),
          () -> assertThat(response.getBody().data().birthDate()).isEqualTo(request.get("birthDate")),
          () -> assertThat(response.getBody().data().email()).isEqualTo(request.get("email")));
    }

    @DisplayName("회원 가입 시에 성별이 없을 경우, 400 Bad Request 응답을 반환한다.")
    @Test
    void returnsBadRequest_whenGenderIsMissing() {
      // arrange
      Map<String, Object> request =
          Map.of(
              "loginId", "loopers01",
              "password", "Password1!",
              "name", "홍길동",
              "birthDate", "1995-05-15",
              "email", "loopers@example.com");

      // act
      ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType =
          new ParameterizedTypeReference<>() {};
      ResponseEntity<ApiResponse<Map<String, Object>>> response =
          testRestTemplate.exchange(
              ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request), responseType);

      // assert
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }
}
