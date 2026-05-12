package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberModelTest {

    @DisplayName("회원을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 정보가 주어지면, 정상적으로 생성된다.")
        @Test
        void createsMember_whenAllFieldsAreValid() {
            // Arrange
            String loginId = "testUser1";
            String password = "Password1!";
            String name = "홍길동";
            String birthDate = "19900101";
            String email = "test@example.com";

            // Act
            MemberModel member = new MemberModel(loginId, password, name, birthDate, email);

            // Assert
            assertThat(member.getLoginId()).isEqualTo(loginId);
        }

        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooShort() {
            // Arrange
            String shortPassword = "Pass1!";

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                    new MemberModel("testUser1", shortPassword, "홍길동", "19900101", "test@example.com")
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            // Arrange
            String birthDate = "19900101";
            String passwordWithBirth = "19900101Ab!";

            // Act
            CoreException result = assertThrows(CoreException.class, () ->
                    new MemberModel("testUser1", passwordWithBirth, "홍길동", birthDate, "test@example.com")
            );

            // Assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("이름을 마스킹할 때, ")
    @Nested
    class MaskName {

        @DisplayName("이름의 마지막 글자가 * 로 바뀐다.")
        @Test
        void masksLastCharacter() {
            // Arrange
            MemberModel member = new MemberModel("testUser1", "Password1!", "홍길동", "19900101", "test@example.com");

            // Act
            String masked = member.getMaskedName();

            // Assert
            assertThat(masked).isEqualTo("홍길*");
        }
    }
}
