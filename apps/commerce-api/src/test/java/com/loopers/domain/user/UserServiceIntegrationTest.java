package com.loopers.domain.user;

import com.loopers.domain.user.enums.UserRole;
import com.loopers.domain.user.vo.BirthDay;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.RawPassword;
import com.loopers.domain.user.vo.UserId;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private final String DEFAULT_USERID   = "user1";
    private final String DEFAULT_PASSWORD = "Dlaxodid1!";
    private final String NEW_PASSWORD     = "Dlaxodid2!";
    private final String DEFAULT_NAME     = "홍길동";
    private final String DEFAULT_BIRTHDAY = "1990-01-01";
    private final String DEFAULT_EMAIL    = "test@test.com";

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel saveDefaultUser() {
        return userRepository.save(new UserModel(
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

        @DisplayName("정상적인 입력이면, 회원이 생성된다.")
        @Test
        void returnsUser_whenInputsAreValid() {
            UserModel result = userService.register(
                    defaultUserId(),
                    defaultRawPassword(),
                    new Name(DEFAULT_NAME),
                    new BirthDay(DEFAULT_BIRTHDAY),
                    new Email(DEFAULT_EMAIL)
            );

            assertThat(result.getUserId().getValue()).isEqualTo(DEFAULT_USERID);
        }

    }

    @DisplayName("회원조회 시,")
    @Nested
    class FindUser {

        @DisplayName("회원이 존재하면 회원정보를 리턴한다.")
        @Test
        void returnsUser_whenUserIdExists() {
            saveDefaultUser();

            UserModel result = userService.getUser(defaultUserId());

            assertThat(result.getUserId().getValue()).isEqualTo(DEFAULT_USERID);
        }
    }

    @DisplayName("비밀번호 변경 시,")
    @Nested
    class ChangePassword {

        @DisplayName("비밀번호 값이 유효하면 비밀번호를 변경한다.")
        @Test
        void changesPassword_whenPasswordIsValid() {
            saveDefaultUser();

            userService.changePassword(defaultUserId(), newRawPassword());

            UserModel saved = userRepository.findByUserId(defaultUserId()).get();
            assertThat(passwordEncoder.matches(NEW_PASSWORD, saved.getPassword().getValue())).isTrue();
        }

    }

}
