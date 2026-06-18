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
    @DisplayName("?뚯썝媛?낆씠 ?뺤긽?곸쑝濡??섑뻾?섍퀬 ??λ맂??")
    void signUp_ShouldSaveUser() {
        // given
        String loginId = "tester01";
        String password = "Password123!";
        String name = "?뚯뒪??;
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        String email = "tester01@example.com";

        // when
        userService.signUp(loginId, password, name, birthDate, email);

        // then
        UserModel saved = userRepository.findByLoginId(loginId).orElseThrow();
        assertThat(saved.getLoginId()).isEqualTo(loginId);
        assertThat(saved.getName()).isEqualTo(name);
        assertThat(saved.getPassword()).isNotEqualTo(password); // ?뷀샇???뺤씤
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("以묐났??loginId濡?媛?????덉쇅媛 諛쒖깮?쒕떎.")
    void signUp_DuplicateLoginId_ShouldThrowException() {
        // given
        String loginId = "tester01";
        userService.signUp(loginId, "Password123!", "?뚯뒪??, LocalDate.of(1990, 1, 1), "tester01@example.com");

        // when & then
        CoreException exception = assertThrows(CoreException.class, () -> 
                userService.signUp(loginId, "AnotherPw123!", "?ㅻⅨ?대쫫", LocalDate.of(1990, 1, 1), "other@example.com")
        );
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.DUPLICATE_LOGIN_ID);
    }

    @Test
    @DisplayName("?꾩씠?붿? 鍮꾨?踰덊샇媛 ?쇱튂?섎㈃ 留덉뒪?밸맂 ?대쫫???ы븿???뚯썝 ?뺣낫瑜?議고쉶?????덈떎.")
    void getUser_CorrectCredentials_ShouldReturnMaskedUserInfo() {
        // given
        String loginId = "tester01";
        String password = "Password123!";
        String name = "?띻만??;
        userService.signUp(loginId, password, name, LocalDate.of(1990, 1, 1), "tester01@example.com");

        // when
        UserInfo result = userService.getUser(loginId, password);

        // then
        assertThat(result.loginId()).isEqualTo(loginId);
        assertThat(result.name()).isEqualTo("?띻만*"); // 留덉뒪???뺤씤
    }

    @Test
    @DisplayName("?대쫫??2湲?먯씤 寃쎌슦 ?앹옄由щ쭔 留덉뒪?밸맂??")
    void getUser_TwoCharsName_ShouldMaskLast() {
        // given
        String loginId = "tester01";
        String password = "Password123!";
        String name = "?띻만";
        userService.signUp(loginId, password, name, LocalDate.of(1990, 1, 1), "tester01@example.com");

        // when
        UserInfo result = userService.getUser(loginId, password);

        // then
        assertThat(result.name()).isEqualTo("??");
    }

    @Test
    @DisplayName("鍮꾨?踰덊샇 ?섏젙 ???뺤긽?곸쑝濡??섏젙?쒕떎.")
    void updatePassword_ShouldUpdate() {
        // given
        String loginId = "tester01";
        String oldPassword = "OldPassword123!";
        String newPassword = "NewPassword123!";
        userService.signUp(loginId, oldPassword, "?뚯뒪??, LocalDate.of(1990, 1, 1), "tester01@example.com");

        // when
        userService.updatePassword(loginId, oldPassword, oldPassword, newPassword);

        // then
        UserInfo info = userService.getUser(loginId, newPassword);
        assertThat(info.loginId()).isEqualTo(loginId);
    }

    @Test
    @DisplayName("鍮꾨?踰덊샇 ?섏젙 ??湲곗〈 鍮꾨?踰덊샇? ?좉퇋 鍮꾨?踰덊샇媛 媛숈쑝硫??덉쇅媛 諛쒖깮?쒕떎.")
    void updatePassword_SamePassword_ShouldThrowException() {
        // given
        String loginId = "tester01";
        String password = "OldPassword123!";
        userService.signUp(loginId, password, "?뚯뒪??, LocalDate.of(1990, 1, 1), "tester01@example.com");

        // when & then
        CoreException exception = assertThrows(CoreException.class, () -> 
                userService.updatePassword(loginId, password, password, password)
        );
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.SAME_PASSWORD_AS_OLD);
    }

    @Test
    @DisplayName("?뚯썝 議고쉶 ??鍮꾨?踰덊샇媛 ?쇱튂?섏? ?딆쑝硫??덉쇅媛 諛쒖깮?쒕떎.")
    void getUser_WrongPassword_ShouldThrowException() {
        // given
        String loginId = "tester01";
        userService.signUp(loginId, "Password123!", "?띻만??, LocalDate.of(1990, 1, 1), "tester01@example.com");

        // when & then
        CoreException exception = assertThrows(CoreException.class, () -> 
                userService.getUser(loginId, "WrongPw123!")
        );
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
    }

    @Test
    @DisplayName("議댁옱?섏? ?딅뒗 ?뚯썝 議고쉶 ???덉쇅媛 諛쒖깮?쒕떎.")
    void getUser_UserNotFound_ShouldThrowException() {
        // when & then
        CoreException exception = assertThrows(CoreException.class, () -> 
                userService.getUser("nonexistent", "Password123!")
        );
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("鍮꾨?踰덊샇 ?섏젙 ???꾩옱 鍮꾨?踰덊샇媛 ?쇱튂?섏? ?딆쑝硫??덉쇅媛 諛쒖깮?쒕떎.")
    void updatePassword_CurrentPasswordMismatch_ShouldThrowException() {
        // given
        String loginId = "tester01";
        String password = "OldPassword123!";
        userService.signUp(loginId, password, "?뚯뒪??, LocalDate.of(1990, 1, 1), "tester01@example.com");

        // when & then
        CoreException exception = assertThrows(CoreException.class, () -> 
                userService.updatePassword(loginId, "WrongCurrentPw!", password, "NewPassword123!")
        );
        assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
    }
}
