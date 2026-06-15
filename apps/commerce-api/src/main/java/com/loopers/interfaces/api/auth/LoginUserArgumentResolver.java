package com.loopers.interfaces.api.auth;

import com.loopers.domain.user.UserCommand;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link LoginUser} 가 붙은 {@code Long} 파라미터에 로그인 사용자의 식별자(userId)를 주입한다.
 * 명세의 식별 방식대로 {@code X-Loopers-LoginId} + {@code X-Loopers-LoginPw} 자격증명을 매칭해 사용자를 식별한다.
 * (토큰/세션/인가 같은 인증 인프라는 과제 스코프가 아니므로 두지 않고, 헤더 자격증명 매칭만 수행한다.)
 */
@RequiredArgsConstructor
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final UserService userService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && parameter.getParameterType().equals(Long.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        String loginId = webRequest.getHeader(HEADER_LOGIN_ID);
        String password = webRequest.getHeader(HEADER_LOGIN_PW);
        if (loginId == null || loginId.isBlank() || password == null || password.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "필수 로그인 헤더(X-Loopers-LoginId, X-Loopers-LoginPw)가 누락되었습니다.");
        }
        UserModel user = userService.authenticate(new UserCommand.Authenticate(loginId, password));
        return user.getId();
    }
}
