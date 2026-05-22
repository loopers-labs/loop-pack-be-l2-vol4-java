package com.loopers.interfaces.api.auth;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class AuthenticatedUserArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    public static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && String.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        String loginId = webRequest.getHeader(HEADER_LOGIN_ID);
        String loginPassword = webRequest.getHeader(HEADER_LOGIN_PW);

        if (isBlank(loginId) || isBlank(loginPassword)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 누락되었습니다.");
        }

        UserModel user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 올바르지 않습니다."));

        if (!passwordEncoder.matches(loginPassword, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 올바르지 않습니다.");
        }

        return user.getLoginId();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
