package com.loopers.domain.user;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private final String DEFAULT_USERID       = "user1";
    private final String DEFAULT_PASSWORD     = "dlaxodid1!";
    private final String NEW_PASSWORD = "dlaxodid2!";
    private final String DEFAULT_NAME         = "홍길동";
    private final String DEFAULT_BIRTHDAY     = "1990-01-01";
    private final String DEFAULT_EMAIL        = "test@test.com";

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입을 할 때,")
    @Nested
    class Register {

        @DisplayName("정상적인 입력이면, 회원이 생성된다.")
        @Test
        void returnsUser_whenInputsAreValid() {
            // act
            UserModel result = userService.register(DEFAULT_USERID, DEFAULT_PASSWORD, DEFAULT_NAME, DEFAULT_BIRTHDAY, DEFAULT_EMAIL);

            // assert
            assertThat(result.getUserid()).isEqualTo(DEFAULT_USERID);
        }
    }

    @DisplayName("회원조회 시,")
    @Nested
    class FindUser {

        @DisplayName("회원이 존재하면 회원정보를 리턴한다.")
        @Test
        void returnsUser_whenUserIdExists() {
            // arrange
            userRepository.save(new UserModel(DEFAULT_USERID, passwordEncoder.encode(DEFAULT_PASSWORD), DEFAULT_NAME, DEFAULT_BIRTHDAY, DEFAULT_EMAIL));

            // act
            UserModel result = userService.getUser(DEFAULT_USERID);

            // assert
            assertThat(result.getUserid()).isEqualTo(DEFAULT_USERID);
        }
    }

    @DisplayName("비밀번호 변경 시,")
    @Nested
    class ChangePassword {

        @DisplayName("비밀번호 값이 유효하면 비밀번호를 변경한다.")
        @Test
        void changesPassword_whenPasswordIsValid() {
            // arrange
            userRepository.save(new UserModel(DEFAULT_USERID, passwordEncoder.encode(DEFAULT_PASSWORD), DEFAULT_NAME, DEFAULT_BIRTHDAY, DEFAULT_EMAIL));

            // act
            userService.changePassword(DEFAULT_USERID, NEW_PASSWORD);

            // assert
            UserModel saved = userRepository.findByUserid(DEFAULT_USERID).get();
            assertThat(passwordEncoder.matches(NEW_PASSWORD, saved.getPassword())).isTrue();
        }
    }
}
