package com.loopers.user.domain;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class UserTest {

    private static final String LOGIN_ID = "loopers01";
    private static final String PASSWORD = "encoded-password";
    private static final String NAME = "김루퍼";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 3, 21);
    private static final String EMAIL = "looper@example.com";

    @Test
    @DisplayName("create 로 생성하면 모든 필드가 저장된다")
    void givenValidFields_whenCreate_thenStoresAllFields() {
        User user = User.create(LOGIN_ID, PASSWORD, NAME, BIRTH_DATE, EMAIL);

        assertAll(
                () -> assertThat(user.getLoginId()).isEqualTo(LOGIN_ID),
                () -> assertThat(user.getPassword()).isEqualTo(PASSWORD),
                () -> assertThat(user.getName()).isEqualTo(NAME),
                () -> assertThat(user.getBirthDate()).isEqualTo(BIRTH_DATE),
                () -> assertThat(user.getEmail()).isEqualTo(EMAIL)
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("로그인 ID가 비어있으면 CoreException이 발생한다")
    void givenBlankLoginId_whenCreate_thenThrowsCoreException(String invalidLoginId) {
        assertThatThrownBy(() -> User.create(invalidLoginId, PASSWORD, NAME, BIRTH_DATE, EMAIL))
            .isInstanceOf(CoreException.class)
            .hasMessageContaining("로그인 ID는 비어있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("비밀번호가 비어있으면 CoreException이 발생한다")
    void givenBlankPassword_whenCreate_thenThrowsCoreException(String invalidPassword) {
        assertThatThrownBy(() -> User.create(LOGIN_ID, invalidPassword, NAME, BIRTH_DATE, EMAIL))
            .isInstanceOf(CoreException.class)
            .hasMessageContaining("비밀번호는 비어있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("이름이 비어있으면 CoreException이 발생한다")
    void givenBlankName_whenCreate_thenThrowsCoreException(String invalidName) {
        assertThatThrownBy(() -> User.create(LOGIN_ID, PASSWORD, invalidName, BIRTH_DATE, EMAIL))
            .isInstanceOf(CoreException.class)
            .hasMessageContaining("이름은 비어있을 수 없습니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("이메일이 비어있으면 CoreException이 발생한다")
    void givenBlankEmail_whenCreate_thenThrowsCoreException(String invalidEmail) {
        assertThatThrownBy(() -> User.create(LOGIN_ID, PASSWORD, NAME, BIRTH_DATE, invalidEmail))
            .isInstanceOf(CoreException.class)
            .hasMessageContaining("이메일은 비어있을 수 없습니다.");
    }

    @Test
    @DisplayName("생년월일이 없으면 CoreException이 발생한다")
    void givenNullBirthDate_whenCreate_thenThrowsCoreException() {
        assertThatThrownBy(() -> User.create(LOGIN_ID, PASSWORD, NAME, null, EMAIL))
            .isInstanceOf(CoreException.class)
            .hasMessageContaining("생년월일은 비어있을 수 없습니다.");
    }

    @Test
    @DisplayName("changePassword 로 비밀번호를 갱신할 수 있다")
    void givenUser_whenChangePassword_thenUpdatesPassword() {
        User user = User.create(LOGIN_ID, PASSWORD, NAME, BIRTH_DATE, EMAIL);

        user.changePassword("new-encoded-password");

        assertThat(user.getPassword()).isEqualTo("new-encoded-password");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("changePassword 에 비어있는 값을 주면 CoreException 이 발생한다")
    void givenUser_whenChangePasswordWithBlank_thenThrowsCoreException(String invalidPassword) {
        User user = User.create(LOGIN_ID, PASSWORD, NAME, BIRTH_DATE, EMAIL);

        assertThatThrownBy(() -> user.changePassword(invalidPassword))
            .isInstanceOf(CoreException.class)
            .hasMessageContaining("비밀번호는 비어있을 수 없습니다.");
    }
}
