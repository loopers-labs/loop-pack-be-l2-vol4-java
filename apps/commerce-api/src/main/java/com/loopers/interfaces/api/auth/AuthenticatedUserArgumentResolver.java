package com.loopers.interfaces.api.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.loopers.domain.user.PasswordEncrypter;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AuthenticatedUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String LOGIN_PASSWORD_HEADER = "X-Loopers-LoginPw";

    private final PasswordEncrypter passwordEncrypter;

    private final UserRepository userRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticatedUser.class)
            && parameter.getParameterType().equals(UserModel.class);
    }

    @Override
    public UserModel resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        String loginId = request.getHeader(LOGIN_ID_HEADER);
        String rawPassword = request.getHeader(LOGIN_PASSWORD_HEADER);

        if (loginId == null || rawPassword == null) {
            throw new CoreException(ErrorType.UNAUTHENTICATED);
        }

        UserModel authenticatedUser = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHENTICATED));

        if (!authenticatedUser.authenticate(rawPassword, passwordEncrypter)) {
            throw new CoreException(ErrorType.UNAUTHENTICATED);
        }

        return authenticatedUser;
    }
}
