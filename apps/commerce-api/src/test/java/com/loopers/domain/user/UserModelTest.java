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
    @DisplayName("濡쒓렇??ID媛 null?대㈃ ?덉쇅媛 諛쒖깮?쒕떎.")
    void validateLoginId_NullId_ShouldThrowException() {
        assertThrows(CoreException.class, () -> UserModel.validateLoginId(null));
    }

    @Test
    @DisplayName("濡쒓렇??ID媛 5??誘몃쭔?대㈃ ?덉쇅媛 諛쒖깮?쒕떎.")
    void validateLoginId_ShortId_ShouldThrowException() {
        assertThrows(CoreException.class, () -> UserModel.validateLoginId("id"));
    }

    @Test
    @DisplayName("濡쒓렇??ID媛 20?먮? 珥덇낵?섎㈃ ?덉쇅媛 諛쒖깮?쒕떎.")
    void validateLoginId_LongId_ShouldThrowException() {
        assertThrows(CoreException.class, () -> UserModel.validateLoginId("a".repeat(21)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tester!!", "?쒓??꾩씠??, "test space"})
    @DisplayName("濡쒓렇??ID ?뺤떇???щ컮瑜댁? ?딆쑝硫??덉쇅媛 諛쒖깮?쒕떎.")
    void validateLoginId_InvalidFormat_ShouldThrowException(String invalidId) {
        CoreException exception = assertThrows(CoreException.class, () -> UserModel.validateLoginId(invalidId));
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_LOGIN_ID);
    }

    @Test
    @DisplayName("鍮꾨?踰덊샇媛 null?대㈃ ?덉쇅媛 諛쒖깮?쒕떎.")
    void validatePassword_NullPassword_ShouldThrowException() {
        assertThrows(CoreException.class, () -> 
                UserModel.validatePassword(null, LocalDate.of(1990, 1, 1)));
    }

    @Test
    @DisplayName("鍮꾨?踰덊샇媛 8??誘몃쭔?대㈃ ?덉쇅媛 諛쒖깮?쒕떎.")
    void validatePassword_ShortPassword_ShouldThrowException() {
        CoreException exception = assertThrows(CoreException.class, () -> 
                UserModel.validatePassword("Pass1!", LocalDate.of(1990, 1, 1)));
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD);
    }

    @Test
    @DisplayName("鍮꾨?踰덊샇???앸뀈?붿씪???ы븿?섎㈃ ?덉쇅媛 諛쒖깮?쒕떎.")
    void validatePassword_ContainsBirthDate_ShouldThrowException() {
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        CoreException exception = assertThrows(CoreException.class, () -> 
                UserModel.validatePassword("p19900101A!", birthDate));
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTHDATE);
    }

    @Test
    @DisplayName("?대쫫??2??誘몃쭔?대㈃ ?덉쇅媛 諛쒖깮?쒕떎.")
    void constructor_ShortName_ShouldThrowException() {
        assertThrows(CoreException.class, () -> 
                UserModel.builder()
                        .loginId("tester01")
                        .password("Password123!")
                        .name("??)
                        .birthDate(LocalDate.of(1990, 1, 1))
                        .email("tester@example.com")
                        .build());
    }

    @Test
    @DisplayName("?대쫫??20?먮? 珥덇낵?섎㈃ ?덉쇅媛 諛쒖깮?쒕떎.")
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
    @DisplayName("?대쫫 ?뺤떇???щ컮瑜댁? ?딆쑝硫??덉쇅媛 諛쒖깮?쒕떎.")
    void constructor_InvalidName_ShouldThrowException() {
        CoreException exception = assertThrows(CoreException.class, () -> 
                UserModel.builder()
                        .loginId("tester01")
                        .password("Password123!")
                        .name("?띻만??")
                        .birthDate(LocalDate.of(1990, 1, 1))
                        .email("tester@example.com")
                        .build());
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_NAME);
    }

    @Test
    @DisplayName("?대찓???뺤떇???щ컮瑜댁? ?딆쑝硫??덉쇅媛 諛쒖깮?쒕떎.")
    void constructor_InvalidEmail_ShouldThrowException() {
        CoreException exception = assertThrows(CoreException.class, () -> 
                UserModel.builder()
                        .loginId("tester01")
                        .password("Password123!")
                        .name("?띻만??)
                        .birthDate(LocalDate.of(1990, 1, 1))
                        .email("invalid-email")
                        .build());
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_EMAIL);
    }

    @Test
    @DisplayName("?앸뀈?붿씪??null?대㈃ ?덉쇅媛 諛쒖깮?쒕떎.")
    void constructor_NullBirthDate_ShouldThrowException() {
        assertThrows(CoreException.class, () -> 
                UserModel.builder()
                        .loginId("tester01")
                        .password("Password123!")
                        .name("?띻만??)
                        .birthDate(null)
                        .email("tester@example.com")
                        .build());
    }

    @Test
    @DisplayName("?앸뀈?붿씪??誘몃옒 ?좎쭨?대㈃ ?덉쇅媛 諛쒖깮?쒕떎.")
    void constructor_FutureBirthDate_ShouldThrowException() {
        CoreException exception = assertThrows(CoreException.class, () -> 
                UserModel.builder()
                        .loginId("tester01")
                        .password("Password123!")
                        .name("?띻만??)
                        .birthDate(LocalDate.now().plusDays(1))
                        .email("tester@example.com")
                        .build());
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_BIRTHDATE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"password123!", "PASSWORD123!", "Password!", "Password123"})
    @DisplayName("鍮꾨?踰덊샇 ?뺤떇???щ컮瑜댁? ?딆쑝硫??덉쇅媛 諛쒖깮?쒕떎 (??뚮Ц???レ옄/?뱀닔臾몄옄 議고빀).")
    void validatePassword_InvalidFormat_ShouldThrowException(String invalidPassword) {
        CoreException exception = assertThrows(CoreException.class, () -> 
                UserModel.validatePassword(invalidPassword, LocalDate.of(1990, 1, 1)));
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?띻만??, "HongGildong"})
    @DisplayName("?щ컮瑜??대쫫 ?뺤떇? ?듦낵?쒕떎.")
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
    @DisplayName("?щ컮瑜??대찓???뺤떇? ?듦낵?쒕떎.")
    void constructor_ValidEmail_ShouldPass(String validEmail) {
        UserModel user = UserModel.builder()
                .loginId("tester01")
                .password("Password123!")
                .name("?띻만??)
                .birthDate(LocalDate.of(1990, 1, 1))
                .email(validEmail)
                .build();
        assertThat(user.getEmail()).isEqualTo(validEmail);
    }

    @Test
    @DisplayName("湲곕낯 ??븷? USER?대떎.")
    void constructor_DefaultRole_ShouldBeUser() {
        UserModel user = UserModel.builder()
                .loginId("tester01")
                .password("Password123!")
                .name("?뚯뒪??)
                .birthDate(LocalDate.of(1990, 1, 1))
                .email("tester@example.com")
                .build();
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
    }
}
