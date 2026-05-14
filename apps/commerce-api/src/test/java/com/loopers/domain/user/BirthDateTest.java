package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BirthDateTest {

  @DisplayName("BirthDate VO를 생성할 때,")
  @Nested
  class Create {

    @DisplayName("yyyy-MM-dd 형식이면 BirthDate 생성에 성공하고 LocalDate 값으로 변환된다.")
    @Test
    void createsBirthDate_whenFormatIsValid() {
      // arrange
      String value = "1995-05-15";

      // act
      BirthDate birthDate = BirthDate.of(value);

      // assert
      assertThat(birthDate.getValue()).isEqualTo(LocalDate.of(1995, 5, 15));
    }

    @DisplayName("yyyy-MM-dd 형식이 아니면 BAD_REQUEST 예외가 발생한다.")
    @Test
    void throwsBadRequestException_whenFormatIsInvalid() {
      // act
      CoreException exception =
          assertThrows(CoreException.class, () -> BirthDate.of("1995/05/15"));

      // assert
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("존재하지 않는 날짜이면 BAD_REQUEST 예외가 발생한다.")
    @Test
    void throwsBadRequestException_whenDateDoesNotExist() {
      // act
      CoreException exception =
          assertThrows(CoreException.class, () -> BirthDate.of("1995-02-30"));

      // assert
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("생년월일이 비어있거나 빈칸이면 BAD_REQUEST 예외가 발생한다.")
    @Test
    void throwsBadRequestException_whenValueIsBlank() {
      // act
      CoreException emptyException = assertThrows(CoreException.class, () -> BirthDate.of(""));
      CoreException blankException = assertThrows(CoreException.class, () -> BirthDate.of("   "));

      // assert
      assertAll(
          () -> assertThat(emptyException.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
          () -> assertThat(blankException.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }
  }
}
