package com.loopers.interfaces.api.queue;

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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link QueueUser} 파라미터 해석기 — 로그인 ID 헤더로 userId를 해석한다(비밀번호 미검증).
 *
 * <p><strong>왜 BCrypt를 안 쓰나</strong>: 대기열 순번 폴링은 사용자당 수십 번 반복 호출되는데,
 * 매번 BCrypt(~70ms CPU)를 돌면 CPU가 포화되어 시스템 전체가 느려진다(부하 테스트로 확인).
 * 대기열은 "대기실"일 뿐이고 실제 구매는 {@code @AuthUser}로 보호되므로, 여기선 식별만 한다.
 *
 * <p><strong>캐시</strong>: loginId→userId 매핑은 불변이라, 첫 조회 후 메모리에 캐시해
 * 반복 폴링이 DB를 다시 타지 않게 한다. 과제 범위에선 무제한 캐시로 충분하다(사용자 수 유한).
 */
@RequiredArgsConstructor
@Component
public class QueueUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";

    private final UserService userService;
    private final Map<String, Long> loginIdToUserId = new ConcurrentHashMap<>();

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(QueueUser.class)
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
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 누락되었습니다.");
        }

        Long cached = loginIdToUserId.get(loginId);
        if (cached != null) {
            return cached;
        }
        // 캐시 미스에만 DB 조회(findByLoginId, BCrypt 없음). 존재하지 않으면 getUser가 예외를 던진다.
        Long userId = userService.getUser(loginId).getId();
        loginIdToUserId.put(loginId, userId);
        return userId;
    }
}
