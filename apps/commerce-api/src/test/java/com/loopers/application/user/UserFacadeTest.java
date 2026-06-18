package com.loopers.application.user;

import com.loopers.domain.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserFacadeTest {

    @InjectMocks
    private UserFacade userFacade;

    @Mock
    private UserService userService;

    @Test
    @DisplayName("?뚯썝媛???붿껌 ??Service??signUp???몄텧?쒕떎.")
    void signUp_ShouldCallService() {
        // when
        userFacade.signUp(
                "tester01", "Password123!", "?뚯뒪??, LocalDate.of(1990, 1, 1), "tester01@example.com"
        );

        // then
        verify(userService).signUp(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("???뺣낫 議고쉶 ???뚯썝 ?뺣낫瑜?諛섑솚?쒕떎.")
    void getMyInfo_ShouldReturnUserInfo() {
        // given
        String loginId = "tester01";
        String password = "Password123!";
        UserInfo userInfo = new UserInfo(
                "tester01",
                "?띻만??,
                LocalDate.of(1990, 1, 1),
                "tester01@example.com"
        );
        given(userService.getUser(loginId, password)).willReturn(userInfo);

        // when
        UserInfo response = userFacade.getMyInfo(loginId, password);

        // then
        assertThat(response.loginId()).isEqualTo("tester01");
        assertThat(response.name()).isEqualTo("?띻만??);
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(response.email()).isEqualTo("tester01@example.com");
    }

    @Test
    @DisplayName("鍮꾨?踰덊샇 ?섏젙 ?붿껌 ??Service??updatePassword瑜??몄텧?쒕떎.")
    void updatePassword_ShouldCallService() {
        // given
        String loginId = "tester01";
        String password = "OldPassword123!";

        // when
        userFacade.updatePassword(loginId, password, "OldPassword123!", "NewPassword123!");

        // then
        verify(userService).updatePassword(any(), any(), any(), any());
    }
}
