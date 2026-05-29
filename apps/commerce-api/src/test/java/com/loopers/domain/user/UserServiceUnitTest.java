package com.loopers.domain.user;

import com.loopers.domain.user.enums.UserRole;
import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.RawPassword;
import com.loopers.domain.user.vo.UserId;
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
    private final String DEFAULT_PASSWORD = "Dlaxodid1!";
    private final String NEW_PASSWORD     = "Dlaxodid2!";
    private final String DEFAULT_NAME     = "홍길동";
    private final String DEFAULT_BIRTHDAY = "1990-01-01";
    private final String DEFAULT_EMAIL    = "test@test.com";

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        sut = new UserService(userRepository, passwordEncoder);
    }

    private void saveDefaultUser() {
        userRepository.save(new UserModel(
                new UserId(DEFAULT_USERID),
                new Password(passwordEncoder.encode(DEFAULT_PASSWORD)),
                new Name(DEFAULT_NAME),
                new BirthDay(DEFAULT_BIRTHDAY),
                new Email(DEFAULT_EMAIL),
                UserRole.USER
        ));
    }

    private UserId defaultUserId() { return new UserId(DEFAULT_USERID); }
    private RawPassword defaultRawPassword() { return new RawPassword(DEFAULT_PASSWORD); }
    private RawPassword newRawPassword() { return new RawPassword(NEW_PASSWORD); }

    @DisplayName("회원가입을 할 때,")
    @Nested
    class Register {

        @DisplayName("유효한 입력이면, 회원이 생성된다.")
        @Test
        void returnsUser_whenInputsAreValid() {
            UserModel result = sut.register(defaultUserId(), defaultRawPassword(), new Name(DEFAULT_NAME), new BirthDay(DEFAULT_BIRTHDAY), new Email(DEFAULT_EMAIL));

            assertThat(result.getUserId().getValue()).isEqualTo(DEFAULT_USERID);
        }

        @DisplayName("이미 사용 중인 아이디로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenUseridAlreadyExists() {
            saveDefaultUser();

            CoreException exception = assertThrows(CoreException.class, () ->
                    sut.register(defaultUserId(), defaultRawPassword(), new Name(DEFAULT_NAME), new BirthDay(DEFAULT_BIRTHDAY), new Email(DEFAULT_EMAIL))
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("회원조회 시,")
    @Nested
    class GetUser {

        @DisplayName("회원이 존재하면, 회원 정보를 반환한다.")
        @Test
        void returnsUser_whenUserExists() {
            saveDefaultUser();

            UserModel result = sut.getUser(defaultUserId());

            assertThat(result.getUserId().getValue()).isEqualTo(DEFAULT_USERID);
        }

        @DisplayName("회원이 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUseridDoesNotExist() {
            CoreException exception = assertThrows(CoreException.class, () ->
                    sut.getUser(defaultUserId())
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("비밀번호 변경 시,")
    @Nested
    class ChangePassword {

        @DisplayName("유효한 새 비밀번호면, 비밀번호가 변경된다.")
        @Test
        void changesPassword_whenNewPasswordIsValid() {
            saveDefaultUser();

            sut.changePassword(defaultUserId(), newRawPassword());

            UserModel updated = sut.getUser(defaultUserId());
            assertThat(passwordEncoder.matches(NEW_PASSWORD, updated.getPassword().getValue())).isTrue();
        }

        @DisplayName("회원이 존재하지 않으면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUseridDoesNotExist() {
            CoreException exception = assertThrows(CoreException.class, () ->
                    sut.changePassword(defaultUserId(), newRawPassword())
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("현재 비밀번호와 동일하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenSamePasswordIsUsed() {
            saveDefaultUser();

            CoreException exception = assertThrows(CoreException.class, () ->
                    sut.changePassword(defaultUserId(), defaultRawPassword())
            );

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
