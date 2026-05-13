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
}
