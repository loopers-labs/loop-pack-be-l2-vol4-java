package com.loopers.interfaces.auth;

import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.UserAuthService;
import com.loopers.domain.user.UserModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserAuthService userAuthService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticatedUser.class)
            && LoginUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        String loginIdHeader = webRequest.getHeader(AuthHeaders.LOGIN_ID);
        String loginPwHeader = webRequest.getHeader(AuthHeaders.LOGIN_PW);
        if (loginIdHeader == null || loginIdHeader.isBlank()
            || loginPwHeader == null || loginPwHeader.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }

        LoginId loginId = parseLoginIdOrUnauthorized(loginIdHeader);
        UserModel user = userAuthService.authenticate(loginId, loginPwHeader);
        return LoginUser.from(user);
    }

    private LoginId parseLoginIdOrUnauthorized(String value) {
        try {
            return LoginId.of(value);
        } catch (CoreException e) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }
    }
}
