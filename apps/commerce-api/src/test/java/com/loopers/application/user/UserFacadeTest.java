package com.loopers.application.user;

import com.loopers.domain.user.Email;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.service.UserAuthService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserName;
import com.loopers.domain.user.service.UserPasswordService;
import com.loopers.domain.user.service.UserSignupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserFacadeTest {

    private static final String VALID_LOGIN_ID = "loopers01";
    private static final String VALID_RAW_PASSWORD = "Aa3!xyz@";
    private static final String ENCODED_PASSWORD = "$2a$10$encodedHash";
    private static final String VALID_NAME = "홍길동";
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(2002, 5, 11);
    private static final String VALID_EMAIL = "test@loopers.com";

    @Mock
    private UserSignupService userSignupService;

    @Mock
    private UserAuthService userAuthService;

    @Mock
    private UserPasswordService userPasswordService;

    @InjectMocks
    private UserFacade userFacade;

    private UserModel sampleUser() {
        return new UserModel(
            new LoginId(VALID_LOGIN_ID),
            ENCODED_PASSWORD,
            new UserName(VALID_NAME),
            VALID_BIRTH_DATE,
            new Email(VALID_EMAIL)
        );
    }

    @DisplayName("회원가입 호출 시")
    @Nested
    class SignUp {

        @DisplayName("UserSignupService.signup에 입력값을 그대로 위임한다")
        @Test
        void delegatesToUserSignupService_whenSignUpIsCalled() {
            // given
            // when
            userFacade.signUp(VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // then
            verify(userSignupService, times(1)).signup(VALID_LOGIN_ID, VALID_RAW_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
        }
    }

    @DisplayName("인증 호출 시")
    @Nested
    class Authenticate {

        @DisplayName("UserAuthService.authenticate에 위임하고 userId를 반환한다")
        @Test
        void returnsUserId_whenAuthenticateIsCalled() {
            // given
            UserModel user = sampleUser();
            when(userAuthService.authenticate(VALID_LOGIN_ID, VALID_RAW_PASSWORD)).thenReturn(user);

            // when
            Long userId = userFacade.authenticate(VALID_LOGIN_ID, VALID_RAW_PASSWORD);

            // then
            assertThat(userId).isEqualTo(user.getId());
        }
    }

    @DisplayName("내 정보 조회 호출 시")
    @Nested
    class GetMyInfo {

        @DisplayName("UserAuthService.getById에 위임하고 UserInfo로 변환해 반환한다")
        @Test
        void returnsUserInfo_whenGetMyInfoIsCalled() {
            // given
            Long userId = 1L;
            UserModel user = sampleUser();
            when(userAuthService.getById(userId)).thenReturn(user);

            // when
            UserInfo info = userFacade.getMyInfo(userId);

            // then
            assertThat(info.loginId()).isEqualTo(VALID_LOGIN_ID);
            assertThat(info.maskedName()).isEqualTo("홍길*");
            assertThat(info.email()).isEqualTo(VALID_EMAIL);
            assertThat(info.birthDate()).isEqualTo(VALID_BIRTH_DATE);
        }
    }

    @DisplayName("비밀번호 변경 호출 시")
    @Nested
    class ChangePassword {

        @DisplayName("UserPasswordService.changePassword에 입력값을 그대로 위임한다")
        @Test
        void delegatesToUserPasswordService_whenChangePasswordIsCalled() {
            // given
            Long userId = 1L;
            String current = VALID_RAW_PASSWORD;
            String next = "NewPw7$z@";

            // when
            userFacade.changePassword(userId, current, next);

            // then
            verify(userPasswordService, times(1)).changePassword(userId, current, next);
        }
    }
}
