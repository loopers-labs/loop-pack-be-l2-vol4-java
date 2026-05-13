package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@ExtendWith(MockitoExtension.class)
class UserModelTest {

    @Mock
    private PasswordEncrypter passwordEncrypter;

    @DisplayName("UserModel을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("모든 raw 데이터가 정책을 통과하면 각 값을 보존한 UserModel이 생성된다.")
        @Test
        void createsUserModel_whenAllRawDataPassPolicy() {
            // arrange
            String rawLoginId = "kyleKim";
            String rawPassword = "Kyle!2030";
            String rawName = "김카일";
            LocalDate rawBirthDate = LocalDate.of(1995, 3, 21);
            String rawEmail = "kyle@example.com";
            String encryptedPasswordValue = "ENCRYPTED";
            given(passwordEncrypter.encrypt(rawPassword)).willReturn(encryptedPasswordValue);

            // act
            UserModel userModel = UserModel.builder()
                .rawLoginId(rawLoginId)
                .rawPassword(rawPassword)
                .rawName(rawName)
                .rawBirthDate(rawBirthDate)
                .rawEmail(rawEmail)
                .passwordEncrypter(passwordEncrypter)
                .build();

            // assert
            assertAll(
                () -> assertThat(userModel.getLoginId()).isEqualTo(LoginId.from(rawLoginId)),
                () -> assertThat(userModel.getName()).isEqualTo(Name.from(rawName)),
                () -> assertThat(userModel.getBirthDate()).isEqualTo(BirthDate.from(rawBirthDate)),
                () -> assertThat(userModel.getEmail()).isEqualTo(Email.from(rawEmail)),
                () -> assertThat(userModel.getEncryptedPassword().value()).isEqualTo(encryptedPasswordValue),
                () -> verify(passwordEncrypter).encrypt(rawPassword)
            );
        }

        @DisplayName("rawPassword에 birthDate가 YYYYMMDD 형식으로 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRawPasswordContainsBirthDateInLongFormat() {
            // arrange
            String rawLoginId = "kyleKim";
            String rawPassword = "Abc19950321!";
            String rawName = "김카일";
            LocalDate rawBirthDate = LocalDate.of(1995, 3, 21);
            String rawEmail = "kyle@example.com";

            // act & assert
            assertThatThrownBy(
                () -> UserModel.builder()
                    .rawLoginId(rawLoginId)
                    .rawPassword(rawPassword)
                    .rawName(rawName)
                    .rawBirthDate(rawBirthDate)
                    .rawEmail(rawEmail)
                    .passwordEncrypter(passwordEncrypter)
                    .build()
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("rawPassword에 birthDate가 YYMMDD 형식으로 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenRawPasswordContainsBirthDateInShortFormat() {
            // arrange
            String rawLoginId = "kyleKim";
            String rawPassword = "Abc950321!";
            String rawName = "김카일";
            LocalDate rawBirthDate = LocalDate.of(1995, 3, 21);
            String rawEmail = "kyle@example.com";

            // act & assert
            assertThatThrownBy(
                () -> UserModel.builder()
                    .rawLoginId(rawLoginId)
                    .rawPassword(rawPassword)
                    .rawName(rawName)
                    .rawBirthDate(rawBirthDate)
                    .rawEmail(rawEmail)
                    .passwordEncrypter(passwordEncrypter)
                    .build()
            )
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호 일치 여부를 확인할 때,")
    @Nested
    class MatchesPassword {

        @DisplayName("rawPassword가 저장된 encryptedPassword와 일치하면 true를 반환한다.")
        @Test
        void returnsTrue_whenRawPasswordMatchesEncryptedPassword() {
            // arrange
            String rawPassword = "Kyle!2030";
            String encryptedPasswordValue = "ENCRYPTED";
            given(passwordEncrypter.encrypt(rawPassword)).willReturn(encryptedPasswordValue);
            given(passwordEncrypter.matches(rawPassword, encryptedPasswordValue)).willReturn(true);

            UserModel userModel = UserModel.builder()
                .rawLoginId("kyleKim")
                .rawPassword(rawPassword)
                .rawName("김카일")
                .rawBirthDate(LocalDate.of(1995, 3, 21))
                .rawEmail("kyle@example.com")
                .passwordEncrypter(passwordEncrypter)
                .build();

            // act
            boolean matchingResult = userModel.matchesPassword(rawPassword, passwordEncrypter);

            // assert
            assertThat(matchingResult).isTrue();
        }

        @DisplayName("rawPassword가 저장된 encryptedPassword와 일치하지 않으면 false를 반환한다.")
        @Test
        void returnsFalse_whenRawPasswordDoesNotMatchEncryptedPassword() {
            // arrange
            String originalRawPassword = "Kyle!2030";
            String wrongRawPassword = "Wrong!2030";
            String encryptedPasswordValue = "ENCRYPTED";
            given(passwordEncrypter.encrypt(originalRawPassword)).willReturn(encryptedPasswordValue);
            given(passwordEncrypter.matches(wrongRawPassword, encryptedPasswordValue)).willReturn(false);

            UserModel userModel = UserModel.builder()
                .rawLoginId("kyleKim")
                .rawPassword(originalRawPassword)
                .rawName("김카일")
                .rawBirthDate(LocalDate.of(1995, 3, 21))
                .rawEmail("kyle@example.com")
                .passwordEncrypter(passwordEncrypter)
                .build();

            // act
            boolean matchingResult = userModel.matchesPassword(wrongRawPassword, passwordEncrypter);

            // assert
            assertThat(matchingResult).isFalse();
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("본문 currentRawPassword가 저장된 encryptedPassword와 일치하지 않으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentRawPasswordDoesNotMatch() {
            // arrange
            String originalRawPassword = "Kyle!2030";
            String wrongCurrentRawPassword = "Wrong!2030";
            String newRawPassword = "Newer!2031";
            String encryptedPasswordValue = "ORIGINAL_ENCRYPTED";
            given(passwordEncrypter.encrypt(originalRawPassword)).willReturn(encryptedPasswordValue);
            given(passwordEncrypter.matches(wrongCurrentRawPassword, encryptedPasswordValue)).willReturn(false);

            UserModel userModel = UserModel.builder()
                .rawLoginId("kyleKim")
                .rawPassword(originalRawPassword)
                .rawName("김카일")
                .rawBirthDate(LocalDate.of(1995, 3, 21))
                .rawEmail("kyle@example.com")
                .passwordEncrypter(passwordEncrypter)
                .build();

            // act & assert
            assertThatThrownBy(() -> userModel.changePassword(wrongCurrentRawPassword, newRawPassword, passwordEncrypter))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("newRawPassword가 currentRawPassword와 평문으로 동일하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewRawPasswordEqualsCurrentRawPassword() {
            // arrange
            String originalRawPassword = "Kyle!2030";
            String encryptedPasswordValue = "ORIGINAL_ENCRYPTED";
            given(passwordEncrypter.encrypt(originalRawPassword)).willReturn(encryptedPasswordValue);
            given(passwordEncrypter.matches(originalRawPassword, encryptedPasswordValue)).willReturn(true);

            UserModel userModel = UserModel.builder()
                .rawLoginId("kyleKim")
                .rawPassword(originalRawPassword)
                .rawName("김카일")
                .rawBirthDate(LocalDate.of(1995, 3, 21))
                .rawEmail("kyle@example.com")
                .passwordEncrypter(passwordEncrypter)
                .build();

            // act & assert
            assertThatThrownBy(() -> userModel.changePassword(originalRawPassword, originalRawPassword, passwordEncrypter))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("newRawPassword에 birthDate가 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewRawPasswordContainsBirthDate() {
            // arrange
            String originalRawPassword = "Kyle!2030";
            String newRawPasswordContainingBirthDate = "Abc19950321!";
            String encryptedPasswordValue = "ORIGINAL_ENCRYPTED";
            given(passwordEncrypter.encrypt(originalRawPassword)).willReturn(encryptedPasswordValue);
            given(passwordEncrypter.matches(originalRawPassword, encryptedPasswordValue)).willReturn(true);

            UserModel userModel = UserModel.builder()
                .rawLoginId("kyleKim")
                .rawPassword(originalRawPassword)
                .rawName("김카일")
                .rawBirthDate(LocalDate.of(1995, 3, 21))
                .rawEmail("kyle@example.com")
                .passwordEncrypter(passwordEncrypter)
                .build();

            // act & assert
            assertThatThrownBy(() -> userModel.changePassword(originalRawPassword, newRawPasswordContainingBirthDate, passwordEncrypter))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("newRawPassword가 비밀번호 RULE을 위반하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewRawPasswordViolatesRule() {
            // arrange
            String originalRawPassword = "Kyle!2030";
            String tooShortNewRawPassword = "Ab1!";
            String encryptedPasswordValue = "ORIGINAL_ENCRYPTED";
            given(passwordEncrypter.encrypt(originalRawPassword)).willReturn(encryptedPasswordValue);
            given(passwordEncrypter.matches(originalRawPassword, encryptedPasswordValue)).willReturn(true);

            UserModel userModel = UserModel.builder()
                .rawLoginId("kyleKim")
                .rawPassword(originalRawPassword)
                .rawName("김카일")
                .rawBirthDate(LocalDate.of(1995, 3, 21))
                .rawEmail("kyle@example.com")
                .passwordEncrypter(passwordEncrypter)
                .build();

            // act & assert
            assertThatThrownBy(() -> userModel.changePassword(originalRawPassword, tooShortNewRawPassword, passwordEncrypter))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("모든 정책을 통과하면 encryptedPassword가 새 해시 값으로 갱신된다.")
        @Test
        void updatesEncryptedPassword_whenAllPolicyPasses() {
            // arrange
            String originalRawPassword = "Kyle!2030";
            String newRawPassword = "Newer!2031";
            String originalEncryptedPasswordValue = "ORIGINAL_ENCRYPTED";
            String newEncryptedPasswordValue = "NEW_ENCRYPTED";
            given(passwordEncrypter.encrypt(originalRawPassword)).willReturn(originalEncryptedPasswordValue);
            given(passwordEncrypter.matches(originalRawPassword, originalEncryptedPasswordValue)).willReturn(true);
            given(passwordEncrypter.encrypt(newRawPassword)).willReturn(newEncryptedPasswordValue);

            UserModel userModel = UserModel.builder()
                .rawLoginId("kyleKim")
                .rawPassword(originalRawPassword)
                .rawName("김카일")
                .rawBirthDate(LocalDate.of(1995, 3, 21))
                .rawEmail("kyle@example.com")
                .passwordEncrypter(passwordEncrypter)
                .build();

            // act
            userModel.changePassword(originalRawPassword, newRawPassword, passwordEncrypter);

            // assert
            assertThat(userModel.getEncryptedPassword().value()).isEqualTo(newEncryptedPasswordValue);
        }
    }
}
