package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceUnitTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private InMemoryUserRepository userRepository;
    private UserService sut;

    private final String DEFAULT_USERID   = "user1";
    private final String DEFAULT_PASSWORD = "dlaxodid1!";
    private final String NEW_PASSWORD = "dlaxodid2!";
    private final String DEFAULT_NAME     = "홍길동";
    private final String DEFAULT_BIRTHDAY = "1990-01-01";
    private final String DEFAULT_EMAIL    = "test@test.com";

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        sut = new UserService(userRepository, passwordEncoder);
    }

    private void saveDefaultUser() {
        userRepository.save(new UserModel(DEFAULT_USERID, passwordEncoder.encode(DEFAULT_PASSWORD), DEFAULT_NAME, DEFAULT_BIRTHDAY, DEFAULT_EMAIL));
    }

    @DisplayName("회원가입을 할 때,")
    @Nested
    class Register {

        @DisplayName("유효한 입력이면, 회원이 생성된다.")
        @Test
        void returnsUser_whenInputsAreValid() {
            // act
            UserModel result = sut.register(DEFAULT_USERID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTHDAY, DEFAULT_EMAIL);

            // assert
            assertThat(result.getUserid()).isEqualTo(DEFAULT_USERID);
        }

        @DisplayName("이미 사용 중인 아이디로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenUseridAlreadyExists() {
            // arrange
            saveDefaultUser();

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                sut.register(DEFAULT_USERID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTHDAY, DEFAULT_EMAIL)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("회원조회 시,")
    @Nested
    class GetUser {

        @DisplayName("회원이 존재하면, 회원 정보를 반환한다.")
        @Test
        void returnsUser_whenUseridExists() {
            // arrange
            saveDefaultUser();

            // act
            UserModel result = sut.getUser(DEFAULT_USERID);

            // assert
            assertThat(result.getUserid()).isEqualTo(DEFAULT_USERID);
        }

        @DisplayName("회원이 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUseridDoesNotExist() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                sut.getUser(DEFAULT_USERID)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("비밀번호 변경 시,")
    @Nested
    class ChangePassword {

        @DisplayName("유효한 새 비밀번호면, 비밀번호가 변경된다.")
        @Test
        void changesPassword_whenNewPasswordIsValid() {
            // arrange
            saveDefaultUser();

            // act
            UserModel result = sut.changePassword(DEFAULT_USERID, NEW_PASSWORD);

            // assert
            assertThat(passwordEncoder.matches(NEW_PASSWORD, result.getPassword())).isTrue();
        }

        @DisplayName("회원이 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUseridDoesNotExist() {
            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                sut.changePassword(DEFAULT_USERID, DEFAULT_PASSWORD)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("현재 비밀번호와 동일하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenSamePasswordIsUsed() {
            // arrange
            saveDefaultUser();

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                sut.changePassword(DEFAULT_USERID, DEFAULT_PASSWORD)
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
