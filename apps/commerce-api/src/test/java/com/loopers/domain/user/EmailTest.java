package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EmailTest {

  @DisplayName("Email VO를 생성할 때,")
  @Nested
  class Create {

    @DisplayName("xx@yy.zz 형식이면 Email 생성에 성공한다.")
    @Test
    void createsEmail_whenFormatIsValid() {
      // arrange
      String value = "loopers@example.com";

      // act
      Email email = Email.of(value);

      // assert
      assertThat(email.getValue()).isEqualTo(value);
    }

    @DisplayName("이메일 형식이 올바르지 않으면 BAD_REQUEST 예외가 발생한다.")
    @Test
    void throwsBadRequestException_whenFormatIsInvalid() {
      // act
      CoreException exception =
          assertThrows(CoreException.class, () -> Email.of("invalid-email"));

      // assert
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("이메일이 비어있거나 빈칸이면 BAD_REQUEST 예외가 발생한다.")
    @Test
    void throwsBadRequestException_whenValueIsBlank() {
      // act
      CoreException emptyException = assertThrows(CoreException.class, () -> Email.of(""));
      CoreException blankException = assertThrows(CoreException.class, () -> Email.of("   "));

      // assert
      assertAll(
          () -> assertThat(emptyException.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
          () -> assertThat(blankException.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }
  }
}
