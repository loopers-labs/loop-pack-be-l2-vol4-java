package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.*;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class UserModelTest {
    @DisplayName("유저 모델을 생성하는 테스트")
    @Nested
    class SignUp {
        String userId;
        String name;
        String password;
        LocalDate birthDate;
        String email;


        @BeforeEach
        void setup() {
            // 모든 과정을 pass 할 수 있는 올바른 값
            String id = "usertest123";
            String name = "홍길동";
            String password = "abc123!@#";
            LocalDate birthDate = LocalDate.of(1995, 6, 10);
            String email = "test@naver.com";
        }

        @DisplayName("정상적인 요청이 온 경우, 정상적으로 생성된다.")
        @Test
        void createsUserModel_whenRequestIsValid() {
            // arrange = given
            // each로 해결

            // act = when
            UserModel userModel = new UserModel(userId, password, name, birthDate, email);

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
            // 이름이 없는 경우
            name = "";

            // act = when
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(userId, password, name, birthDate, email);
            });

            // assert = then
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("이메일 형식이 올바르지 않은 경우, 예외가 발생한다.")
        @Test
        void throwsException_whenEmailIsWrong() {
            // arrange = given
            email = "abdfsd";

            // act = when
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(userId, password, name, birthDate, email);
            });

            // assert = then
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }

        @DisplayName("userId에 특수 문자가 들어가 있는 경우, 예외가 발생한다.")
        @Test
        void throwsException_whenUserIdIncludesSpecialCharacter() {
            // arrange = given
            userId = 'abdf$2341!!';

            // act = when
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(userId, password, name, birthDate, email);
            });

            // assert = then
            assertEquals(ErrorType.BAD_REQUEST, result.getErrorType());
        }
    }
}
