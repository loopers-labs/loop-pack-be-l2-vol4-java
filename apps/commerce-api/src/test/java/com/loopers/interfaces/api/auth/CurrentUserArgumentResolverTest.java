package com.loopers.interfaces.api.auth;

import com.loopers.domain.user.UserModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CurrentUserArgumentResolverTest {

    private CurrentUserArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CurrentUserArgumentResolver();
    }

    @DisplayName("supportsParameter()를 호출할 때,")
    @Nested
    class SupportsParameter {

        @DisplayName("Given @CurrentUser 애노테이션이 있는 파라미터 / When 지원 여부 확인 / Then true를 반환한다.")
        @Test
        void returnsTrue_whenParameterHasCurrentUserAnnotation() {
            // arrange
            MethodParameter parameter = mock(MethodParameter.class);
            given(parameter.hasParameterAnnotation(CurrentUser.class)).willReturn(true);

            // act & assert
            assertThat(resolver.supportsParameter(parameter)).isTrue();
        }

        @DisplayName("Given @CurrentUser 애노테이션이 없는 파라미터 / When 지원 여부 확인 / Then false를 반환한다.")
        @Test
        void returnsFalse_whenParameterHasNoCurrentUserAnnotation() {
            // arrange
            MethodParameter parameter = mock(MethodParameter.class);
            given(parameter.hasParameterAnnotation(CurrentUser.class)).willReturn(false);

            // act & assert
            assertThat(resolver.supportsParameter(parameter)).isFalse();
        }
    }

    @DisplayName("resolveArgument()를 호출할 때,")
    @Nested
    class ResolveArgument {

        @DisplayName("Given request attribute에 currentUser가 존재 / When 파라미터 resolve / Then UserModel이 반환된다.")
        @Test
        void returnsCurrentUser_whenAttributeIsSet() throws Exception {
            // arrange
            UserModel user = new UserModel("user123", "encoded!", "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setAttribute("currentUser", user);

            // act
            Object result = resolver.resolveArgument(null, null, new ServletWebRequest(request), null);

            // assert
            assertThat(result).isEqualTo(user);
        }
    }
}
