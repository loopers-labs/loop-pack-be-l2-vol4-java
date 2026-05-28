package com.loopers.interfaces.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LoginUserArgumentResolverTest {

    @Mock
    private MethodParameter parameter;

    @Mock
    private ModelAndViewContainer mavContainer;

    @Mock
    private NativeWebRequest webRequest;

    @Mock
    private WebDataBinderFactory binderFactory;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private LoginUserArgumentResolver resolver;

    @DisplayName("supportsParameter")
    @Nested
    class SupportsParameter {

        @DisplayName("[ECP] @LoginUser 어노테이션이 있는 파라미터는 true를 반환한다.")
        @Test
        void returnsTrue_whenParameterHasLoginUserAnnotation() {
            // arrange
            given(parameter.hasParameterAnnotation(LoginUser.class)).willReturn(true);

            // act & assert
            assertTrue(resolver.supportsParameter(parameter));
        }

        @DisplayName("[ECP] @LoginUser 어노테이션이 없는 파라미터는 false를 반환한다.")
        @Test
        void returnsFalse_whenParameterHasNoLoginUserAnnotation() {
            // arrange
            given(parameter.hasParameterAnnotation(LoginUser.class)).willReturn(false);

            // act & assert
            assertFalse(resolver.supportsParameter(parameter));
        }
    }

    @DisplayName("resolveArgument")
    @Nested
    class ResolveArgument {

        @DisplayName("[ECP] request attribute에서 userId를 추출하여 반환한다.")
        @Test
        void returnsUserId_fromRequestAttribute() {
            // arrange
            given(webRequest.getNativeRequest(HttpServletRequest.class)).willReturn(request);
            given(request.getAttribute("userId")).willReturn(1L);

            // act
            Object result = resolver.resolveArgument(parameter, mavContainer, webRequest, binderFactory);

            // assert
            assertEquals(1L, result);
        }
    }
}
