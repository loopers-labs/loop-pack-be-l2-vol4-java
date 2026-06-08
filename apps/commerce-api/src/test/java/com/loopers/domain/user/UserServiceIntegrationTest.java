package com.loopers.domain.user;

import com.loopers.application.user.UserInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("회원가입이 정상적으로 수행되고 저장된다.")
    void signUp_ShouldSaveUser() {
        // given
        String loginId = "tester01";
        String password = "Password123!";
        String name = "테스터";
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        String email = "tester01@example.com";

        // when
        userService.signUp(loginId, password, name, birthDate, email);

        // then
        UserModel saved = userRepository.findByLoginId(loginId).orElseThrow();
        assertThat(saved.getLoginId()).isEqualTo(loginId);
        assertThat(saved.getName()).isEqualTo(name);
        assertThat(saved.getPassword()).isNotEqualTo(password); // 암호화 확인
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("중복된 loginId로 가입 시 예외가 발생한다.")
    void signUp_DuplicateLoginId_ShouldThrowException() {
        // given
        String loginId = "tester01";
        userService.signUp(loginId, "Password123!", "테스터", LocalDate.of(1990, 1, 1), "tester01@example.com");

        // when & then
        CoreException exception = assertThrows(CoreException.class, () -> 
                userService.signUp(loginId, "AnotherPw123!", "다른이름", LocalDate.of(1990, 1, 1), "other@example.com")
        );
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.DUPLICATE_LOGIN_ID);
    }

    @Test
    @DisplayName("아이디와 비밀번호가 일치하면 마스킹된 이름이 포함된 회원 정보를 조회할 수 있다.")
    void getUser_CorrectCredentials_ShouldReturnMaskedUserInfo() {
        // given
        String loginId = "tester01";
        String password = "Password123!";
        String name = "홍길동";
        userService.signUp(loginId, password, name, LocalDate.of(1990, 1, 1), "tester01@example.com");

        // when
        UserInfo result = userService.getUser(loginId, password);

        // then
        assertThat(result.loginId()).isEqualTo(loginId);
        assertThat(result.name()).isEqualTo("홍길*"); // 마스킹 확인
    }

    @Test
    @DisplayName("이름이 2글자인 경우 끝자리만 마스킹된다.")
    void getUser_TwoCharsName_ShouldMaskLast() {
        // given
        String loginId = "tester01";
        String password = "Password123!";
        String name = "홍길";
        userService.signUp(loginId, password, name, LocalDate.of(1990, 1, 1), "tester01@example.com");

        // when
        UserInfo result = userService.getUser(loginId, password);

        // then
        assertThat(result.name()).isEqualTo("홍*");
    }

    @Test
    @DisplayName("비밀번호 수정 시 정상적으로 수정된다.")
    void updatePassword_ShouldUpdate() {
        // given
        String loginId = "tester01";
        String oldPassword = "OldPassword123!";
        String newPassword = "NewPassword123!";
        userService.signUp(loginId, oldPassword, "테스터", LocalDate.of(1990, 1, 1), "tester01@example.com");

        // when
        userService.updatePassword(loginId, oldPassword, oldPassword, newPassword);

        // then
        UserInfo info = userService.getUser(loginId, newPassword);
        assertThat(info.loginId()).isEqualTo(loginId);
    }

    @Test
    @DisplayName("비밀번호 수정 시 기존 비밀번호와 신규 비밀번호가 같으면 예외가 발생한다.")
    void updatePassword_SamePassword_ShouldThrowException() {
        // given
        String loginId = "tester01";
        String password = "OldPassword123!";
        userService.signUp(loginId, password, "테스터", LocalDate.of(1990, 1, 1), "tester01@example.com");

        // when & then
        CoreException exception = assertThrows(CoreException.class, () -> 
                userService.updatePassword(loginId, password, password, password)
        );
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.SAME_PASSWORD_AS_OLD);
    }

    @Test
    @DisplayName("회원 조회 시 비밀번호가 일치하지 않으면 예외가 발생한다.")
    void getUser_WrongPassword_ShouldThrowException() {
        // given
        String loginId = "tester01";
        userService.signUp(loginId, "Password123!", "홍길동", LocalDate.of(1990, 1, 1), "tester01@example.com");

        // when & then
        CoreException exception = assertThrows(CoreException.class, () -> 
                userService.getUser(loginId, "WrongPw123!")
        );
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
    }

    @Test
    @DisplayName("존재하지 않는 회원 조회 시 예외가 발생한다.")
    void getUser_UserNotFound_ShouldThrowException() {
        // when & then
        CoreException exception = assertThrows(CoreException.class, () -> 
                userService.getUser("nonexistent", "Password123!")
        );
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("비밀번호 수정 시 현재 비밀번호가 일치하지 않으면 예외가 발생한다.")
    void updatePassword_CurrentPasswordMismatch_ShouldThrowException() {
        // given
        String loginId = "tester01";
        String password = "OldPassword123!";
        userService.signUp(loginId, password, "테스터", LocalDate.of(1990, 1, 1), "tester01@example.com");

        // when & then
        CoreException exception = assertThrows(CoreException.class, () -> 
                userService.updatePassword(loginId, "WrongCurrentPw!", password, "NewPassword123!")
        );
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
    }
}
