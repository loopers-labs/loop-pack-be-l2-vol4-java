package com.loopers.support.fixture;

import com.loopers.application.user.ChangePasswordCommand;
import com.loopers.application.user.SignUpCommand;
import com.loopers.domain.user.User;

import java.util.HashMap;
import java.util.Map;

public final class UserFixture {

  public static final String LOGIN_ID = "loopers01";
  public static final String PASSWORD = "Password1!";
  public static final String NAME = "홍길동";
  public static final String BIRTH_DATE = "1995-05-15";
  public static final String EMAIL = "loopers@example.com";
  public static final String GENDER = "MALE";

  public static final String NEW_PASSWORD = "NewPass2@";

  private UserFixture() {}

  public static User defaultUser() {
    return User.create(LOGIN_ID, PASSWORD, NAME, BIRTH_DATE, EMAIL);
  }

  public static SignUpCommand defaultSignUpCommand() {
    return new SignUpCommand(LOGIN_ID, PASSWORD, NAME, BIRTH_DATE, EMAIL);
  }

  public static ChangePasswordCommand defaultChangePasswordCommand() {
    return new ChangePasswordCommand(PASSWORD, NEW_PASSWORD);
  }

  public static Map<String, Object> defaultSignUpRequest() {
    return Map.of(
        "loginId", LOGIN_ID,
        "password", PASSWORD,
        "name", NAME,
        "birthDate", BIRTH_DATE,
        "email", EMAIL,
        "gender", GENDER);
  }

  public static Map<String, Object> signUpRequestWithoutGender() {
    Map<String, Object> request = new HashMap<>(defaultSignUpRequest());
    request.remove("gender");
    return request;
  }

  public static Map<String, Object> defaultChangePasswordRequest() {
    return changePasswordRequest(PASSWORD, NEW_PASSWORD);
  }

  public static Map<String, Object> changePasswordRequest(String currentPassword, String newPassword) {
    return Map.of(
        "currentPassword", currentPassword,
        "newPassword", newPassword);
  }
}
