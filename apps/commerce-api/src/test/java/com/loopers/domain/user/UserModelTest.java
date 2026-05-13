package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class UserModelTest {
    @DisplayName("유저 모델을 생성하는 테스트")
    @Nested
    class SignUp {

        @DisplayName("정상적인 요청이 온 경우, 정상적으로 생성된다.")
        @Test
        void createsUserModel_whenRequestIsValid() {
            // arrange = given
            String id = "usertest123";
            String name = "홍길동";
            String password = "abc123!@#";
            LocalDate birthDate = LocalDate.of(1995, 6, 10);
            String email = "test@naver.com";

            // act = when
            UserModel userModel = new UserModel(id, password, name, birthDate, email);

            // assert = then
            assertAll(
                () -> assertEquals(id, userModel.getUserId()),
                () -> assertEquals(name, userModel.getName()),
                () -> assertEquals(birthDate, userModel.getBirthDate()),
                () -> assertEquals(email, userModel.getEmail())
            );
        }

        @DisplayName("이름이 없는 경우, 예외가 발생한다.")
        @Test
        void throwsException_whenNameIsMissing() {
            // arrange = given
            String id = "usertest123";
            String name = "";
            String password = "abc123!@#";
            LocalDate birthDate = LocalDate.of(1995, 6, 10);
            String email = "test@naver.com";

            // act = when
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(id, password, name, birthDate, email);
            });

            // assert = then
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("이메일 형식이 올바르지 않은 경우, 예외가 발생한다.")
        @Test
        void throwsException_whenEmailIsWrong() {
            // arrange = given
            String id = "usertest123";
            String name = "홍길동";
            String password = "abc123!@#";
            LocalDate birthDate = LocalDate.of(1995, 6, 10);
            String email = "abdfsd";

            // act = when
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(id, password, name, birthDate, email);
            });

            // assert = then
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }
    }
}
