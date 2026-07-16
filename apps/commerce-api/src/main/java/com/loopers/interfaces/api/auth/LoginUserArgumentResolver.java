package com.loopers.interfaces.api.auth;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
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
 * 실제 인증은 {@link HeaderAuthenticator} 가 수행한다 — 인터셉터가 먼저 인증한 요청이면 그 결과를 재사용한다.
 */
@RequiredArgsConstructor
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final HeaderAuthenticator headerAuthenticator;

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
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "HTTP 요청 컨텍스트를 확인할 수 없습니다.");
        }
        return headerAuthenticator.resolveUserId(request);
    }
}
