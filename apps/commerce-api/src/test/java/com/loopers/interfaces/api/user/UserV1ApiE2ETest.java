package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.dto.UserResponse;
import com.loopers.support.fixture.UserFixture;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

  private static final String ENDPOINT_USER = "/api/v1/users";
  private static final String ENDPOINT_MY_PROFILE = ENDPOINT_USER + "/{loginId}";
  private static final String ENDPOINT_CHANGE_PASSWORD = ENDPOINT_USER + "/{loginId}/password";

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
      Map<String, Object> request = UserFixture.defaultSignUpRequest();

      // act
      ParameterizedTypeReference<ApiResponse<UserResponse>> responseType =
          new ParameterizedTypeReference<>() {};
      ResponseEntity<ApiResponse<UserResponse>> response =
          testRestTemplate.exchange(
              ENDPOINT_USER, HttpMethod.POST, new HttpEntity<>(request), responseType);

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
      Map<String, Object> request = UserFixture.signUpRequestWithoutGender();

      // act
      ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType =
          new ParameterizedTypeReference<>() {};
      ResponseEntity<ApiResponse<Map<String, Object>>> response =
          testRestTemplate.exchange(
              ENDPOINT_USER, HttpMethod.POST, new HttpEntity<>(request), responseType);

      // assert
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @DisplayName("GET /api/v1/users/{loginId}")
  @Nested
  class MyProfile {

    @DisplayName("내 정보 조회에 성공할 경우, 해당하는 유저 정보를 응답으로 반환한다.")
    @Test
    void returnsUserInfo_whenLookupSucceeds() {
      // arrange
      testRestTemplate.exchange(
          ENDPOINT_USER,
          HttpMethod.POST,
          new HttpEntity<>(UserFixture.defaultSignUpRequest()),
          new ParameterizedTypeReference<ApiResponse<UserResponse>>() {});

      // act
      ParameterizedTypeReference<ApiResponse<UserResponse>> responseType =
          new ParameterizedTypeReference<>() {};
      ResponseEntity<ApiResponse<UserResponse>> response =
          testRestTemplate.exchange(
              ENDPOINT_MY_PROFILE, HttpMethod.GET, null, responseType, UserFixture.LOGIN_ID);

      // assert
      assertAll(
          () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
          () -> assertThat(response.getBody()).isNotNull(),
          () -> assertThat(response.getBody().data()).isNotNull(),
          () -> assertThat(response.getBody().data().id()).isNotNull(),
          () -> assertThat(response.getBody().data().loginId()).isEqualTo(UserFixture.LOGIN_ID),
          () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
          () -> assertThat(response.getBody().data().birthDate()).isEqualTo(UserFixture.BIRTH_DATE),
          () -> assertThat(response.getBody().data().email()).isEqualTo(UserFixture.EMAIL));
    }

    @DisplayName("존재하지 않는 ID 로 조회할 경우, 404 Not Found 응답을 반환한다.")
    @Test
    void returnsNotFound_whenUserDoesNotExist() {
      // act
      ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType =
          new ParameterizedTypeReference<>() {};
      ResponseEntity<ApiResponse<Map<String, Object>>> response =
          testRestTemplate.exchange(
              ENDPOINT_MY_PROFILE, HttpMethod.GET, null, responseType, "nonexistent");

      // assert
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  @DisplayName("PATCH /api/v1/users/{loginId}/password")
  @Nested
  class ChangePassword {

    @DisplayName("비밀번호 변경에 성공할 경우, 200 OK 응답을 반환한다.")
    @Test
    void returnsOk_whenChangePasswordSucceeds() {
      // arrange
      signUpDefault();
      Map<String, Object> request = UserFixture.defaultChangePasswordRequest();

      // act
      ParameterizedTypeReference<ApiResponse<Object>> responseType =
          new ParameterizedTypeReference<>() {};
      ResponseEntity<ApiResponse<Object>> response =
          testRestTemplate.exchange(
              ENDPOINT_CHANGE_PASSWORD,
              HttpMethod.PATCH,
              new HttpEntity<>(request),
              responseType,
              UserFixture.LOGIN_ID);

      // assert
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면 400 Bad Request 응답을 반환한다.")
    @Test
    void returnsBadRequest_whenNewPasswordEqualsCurrentPassword() {
      // arrange
      signUpDefault();
      Map<String, Object> request =
          UserFixture.changePasswordRequest(UserFixture.PASSWORD, UserFixture.PASSWORD);

      // act
      ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType =
          new ParameterizedTypeReference<>() {};
      ResponseEntity<ApiResponse<Map<String, Object>>> response =
          testRestTemplate.exchange(
              ENDPOINT_CHANGE_PASSWORD,
              HttpMethod.PATCH,
              new HttpEntity<>(request),
              responseType,
              UserFixture.LOGIN_ID);

      // assert
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @DisplayName("현재 비밀번호가 일치하지 않으면 400 Bad Request 응답을 반환한다.")
    @Test
    void returnsBadRequest_whenCurrentPasswordDoesNotMatch() {
      // arrange
      signUpDefault();
      Map<String, Object> request =
          UserFixture.changePasswordRequest("Wrong1!@", UserFixture.NEW_PASSWORD);

      // act
      ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType =
          new ParameterizedTypeReference<>() {};
      ResponseEntity<ApiResponse<Map<String, Object>>> response =
          testRestTemplate.exchange(
              ENDPOINT_CHANGE_PASSWORD,
              HttpMethod.PATCH,
              new HttpEntity<>(request),
              responseType,
              UserFixture.LOGIN_ID);

      // assert
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private void signUpDefault() {
      testRestTemplate.exchange(
          ENDPOINT_USER,
          HttpMethod.POST,
          new HttpEntity<>(UserFixture.defaultSignUpRequest()),
          new ParameterizedTypeReference<ApiResponse<UserResponse>>() {});
    }
  }
}
