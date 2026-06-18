package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    @Test
    @DisplayName("로그인 ID가 null이면 예외가 발생한다.")
    void validateLoginId_NullId_ShouldThrowException() {
        assertThrows(CoreException.class, () -> UserModel.validateLoginId(null));
    }

    @Test
    @DisplayName("로그인 ID가 5자 미만이면 예외가 발생한다.")
    void validateLoginId_ShortId_ShouldThrowException() {
        assertThrows(CoreException.class, () -> UserModel.validateLoginId("id"));
    }

    @Test
    @DisplayName("로그인 ID가 20자를 초과하면 예외가 발생한다.")
    void validateLoginId_LongId_ShouldThrowException() {
        assertThrows(CoreException.class, () -> UserModel.validateLoginId("a".repeat(21)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tester!!", "한글아이디", "test space"})
    @DisplayName("로그인 ID 형식이 올바르지 않으면 예외가 발생한다.")
    void validateLoginId_InvalidFormat_ShouldThrowException(String invalidId) {
        CoreException exception = assertThrows(CoreException.class, () -> UserModel.validateLoginId(invalidId));
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_LOGIN_ID);
    }

    @Test
    @DisplayName("비밀번호가 null이면 예외가 발생한다.")
    void validatePassword_NullPassword_ShouldThrowException() {
        assertThrows(CoreException.class, () -> 
                UserModel.validatePassword(null, LocalDate.of(1990, 1, 1)));
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 예외가 발생한다.")
    void validatePassword_ShortPassword_ShouldThrowException() {
        CoreException exception = assertThrows(CoreException.class, () -> 
                UserModel.validatePassword("Pass1!", LocalDate.of(1990, 1, 1)));
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD);
    }

    @Test
    @DisplayName("비밀번호에 생년월일이 포함되면 예외가 발생한다.")
    void validatePassword_ContainsBirthDate_ShouldThrowException() {
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        CoreException exception = assertThrows(CoreException.class, () -> 
                UserModel.validatePassword("p19900101A!", birthDate));
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTHDATE);
    }

    @Test
    @DisplayName("이름이 2자 미만이면 예외가 발생한다.")
    void constructor_ShortName_ShouldThrowException() {
        assertThrows(CoreException.class, () -> 
                UserModel.builder()
                        .loginId("tester01")
                        .password("Password123!")
                        .name("홍")
                        .birthDate(LocalDate.of(1990, 1, 1))
                        .email("tester@example.com")
                        .build());
    }

    @Test
    @DisplayName("이름이 20자를 초과하면 예외가 발생한다.")
    void constructor_LongName_ShouldThrowException() {
        assertThrows(CoreException.class, () -> 
                UserModel.builder()
                        .loginId("tester01")
                        .password("Password123!")
                        .name("a".repeat(21))
                        .birthDate(LocalDate.of(1990, 1, 1))
                        .email("tester@example.com")
                        .build());
    }

    @Test
    @DisplayName("이름 형식이 올바르지 않으면 예외가 발생한다.")
    void constructor_InvalidName_ShouldThrowException() {
        CoreException exception = assertThrows(CoreException.class, () -> 
                UserModel.builder()
                        .loginId("tester01")
                        .password("Password123!")
                        .name("홍길동1")
                        .birthDate(LocalDate.of(1990, 1, 1))
                        .email("tester@example.com")
                        .build());
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_NAME);
    }

    @Test
    @DisplayName("이메일 형식이 올바르지 않으면 예외가 발생한다.")
    void constructor_InvalidEmail_ShouldThrowException() {
        CoreException exception = assertThrows(CoreException.class, () -> 
                UserModel.builder()
                        .loginId("tester01")
                        .password("Password123!")
                        .name("홍길동")
                        .birthDate(LocalDate.of(1990, 1, 1))
                        .email("invalid-email")
                        .build());
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_EMAIL);
    }

    @Test
    @DisplayName("생년월일이 null이면 예외가 발생한다.")
    void constructor_NullBirthDate_ShouldThrowException() {
        assertThrows(CoreException.class, () -> 
                UserModel.builder()
                        .loginId("tester01")
                        .password("Password123!")
                        .name("홍길동")
                        .birthDate(null)
                        .email("tester@example.com")
                        .build());
    }

    @Test
    @DisplayName("생년월일이 미래 날짜이면 예외가 발생한다.")
    void constructor_FutureBirthDate_ShouldThrowException() {
        CoreException exception = assertThrows(CoreException.class, () -> 
                UserModel.builder()
                        .loginId("tester01")
                        .password("Password123!")
                        .name("홍길동")
                        .birthDate(LocalDate.now().plusDays(1))
                        .email("tester@example.com")
                        .build());
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_BIRTHDATE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"password123!", "PASSWORD123!", "Password!", "Password123"})
    @DisplayName("비밀번호 형식이 올바르지 않으면 예외가 발생한다 (대소문자/숫자/특수문자 조합).")
    void validatePassword_InvalidFormat_ShouldThrowException(String invalidPassword) {
        CoreException exception = assertThrows(CoreException.class, () -> 
                UserModel.validatePassword(invalidPassword, LocalDate.of(1990, 1, 1)));
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"홍길동", "HongGildong"})
    @DisplayName("올바른 이름 형식은 통과한다.")
    void constructor_ValidName_ShouldPass(String validName) {
        UserModel user = UserModel.builder()
                .loginId("tester01")
                .password("Password123!")
                .name(validName)
                .birthDate(LocalDate.of(1990, 1, 1))
                .email("tester@example.com")
                .build();
        assertThat(user.getName()).isEqualTo(validName);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tester@example.com", "test.user@company.co.kr"})
    @DisplayName("올바른 이메일 형식은 통과한다.")
    void constructor_ValidEmail_ShouldPass(String validEmail) {
        UserModel user = UserModel.builder()
                .loginId("tester01")
                .password("Password123!")
                .name("홍길동")
                .birthDate(LocalDate.of(1990, 1, 1))
                .email(validEmail)
                .build();
        assertThat(user.getEmail()).isEqualTo(validEmail);
    }

    @Test
    @DisplayName("기본 역할은 USER이다.")
    void constructor_DefaultRole_ShouldBeUser() {
        UserModel user = UserModel.builder()
                .loginId("tester01")
                .password("Password123!")
                .name("테스터")
                .birthDate(LocalDate.of(1990, 1, 1))
                .email("tester@example.com")
                .build();
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
    }
}
