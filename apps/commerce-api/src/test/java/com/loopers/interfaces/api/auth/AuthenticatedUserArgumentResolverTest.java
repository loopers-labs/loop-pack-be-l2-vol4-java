package com.loopers.interfaces.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;

import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserArgumentResolverTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncrypter passwordEncrypter;

    @InjectMocks
    private AuthenticatedUserArgumentResolver resolver;

    private MockHttpServletRequest request;
    private NativeWebRequest webRequest;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        webRequest = new ServletWebRequest(request);
    }

    @DisplayName("본인 인증된 회원을 해석할 때,")
    @Nested
    class ResolveArgument {

        @DisplayName("두 헤더가 모두 있고 회원이 존재하고 비밀번호가 일치하면 회원 식별자를 가진 AuthenticatedUser를 반환한다.")
        @Test
        void returnsAuthenticatedUser_whenHeadersValidAndAuthenticated() {
            // arrange
            String rawLoginId = "kyleKim";
            String rawPassword = "Kyle!2030";
            Long userId = 1L;
            request.addHeader("X-Loopers-LoginId", rawLoginId);
            request.addHeader("X-Loopers-LoginPw", rawPassword);

            UserModel storedUser = mock(UserModel.class);
            given(storedUser.getId()).willReturn(userId);
            given(storedUser.authenticate(rawPassword, passwordEncrypter)).willReturn(true);
            given(userRepository.findByLoginId(rawLoginId)).willReturn(Optional.of(storedUser));

            // act
            Object resolvedUser = resolver.resolveArgument(null, null, webRequest, null);

            // assert
            assertThat(resolvedUser)
                .isInstanceOf(AuthenticatedUser.class)
                .extracting("userId")
                .isEqualTo(userId);
        }

        @DisplayName("X-Loopers-LoginId 헤더가 누락되면 UNAUTHENTICATED 예외가 발생한다.")
        @Test
        void throwsUnauthenticated_whenLoginIdHeaderIsMissing() {
            // arrange
            request.addHeader("X-Loopers-LoginPw", "Kyle!2030");

            // act & assert
            assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.UNAUTHENTICATED);
        }

        @DisplayName("X-Loopers-LoginPw 헤더가 누락되면 UNAUTHENTICATED 예외가 발생한다.")
        @Test
        void throwsUnauthenticated_whenLoginPasswordHeaderIsMissing() {
            // arrange
            request.addHeader("X-Loopers-LoginId", "kyleKim");

            // act & assert
            assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.UNAUTHENTICATED);
        }

        @DisplayName("로그인 ID에 해당하는 회원이 존재하지 않으면 UNAUTHENTICATED 예외가 발생한다.")
        @Test
        void throwsUnauthenticated_whenUserNotFound() {
            // arrange
            String rawLoginId = "kyleKim";
            request.addHeader("X-Loopers-LoginId", rawLoginId);
            request.addHeader("X-Loopers-LoginPw", "Kyle!2030");
            given(userRepository.findByLoginId(rawLoginId)).willReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.UNAUTHENTICATED);
        }

        @DisplayName("비밀번호가 일치하지 않으면 UNAUTHENTICATED 예외가 발생한다.")
        @Test
        void throwsUnauthenticated_whenPasswordDoesNotMatch() {
            // arrange
            String rawLoginId = "kyleKim";
            String wrongRawPassword = "Wrong!2030";
            request.addHeader("X-Loopers-LoginId", rawLoginId);
            request.addHeader("X-Loopers-LoginPw", wrongRawPassword);

            UserModel storedUser = mock(UserModel.class);
            given(storedUser.authenticate(wrongRawPassword, passwordEncrypter)).willReturn(false);
            given(userRepository.findByLoginId(rawLoginId)).willReturn(Optional.of(storedUser));

            // act & assert
            assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType")
                .isEqualTo(ErrorType.UNAUTHENTICATED);
        }
    }
}
