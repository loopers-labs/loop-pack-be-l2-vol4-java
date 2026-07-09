package com.loopers.interfaces.api.auth;

import com.loopers.domain.user.UserCommand;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code X-Loopers-LoginId/Pw} 자격증명 헤더로 사용자를 식별한다.
 *
 * <p>결과 userId 를 request attribute 로 캐싱한다 — 한 요청을 인터셉터(입장 토큰 검증)와
 * ArgumentResolver({@link LoginUser})가 각각 인증하면 같은 요청에 인증 DB 조회가 2회 나간다.
 * 대기열은 DB 를 보호하려는 장치이므로, 그 장치가 인증 부하를 배가하지 않게 attribute 로 공유한다.</p>
 */
@RequiredArgsConstructor
@Component
public class HeaderAuthenticator {

    public static final String USER_ID_ATTRIBUTE = HeaderAuthenticator.class.getName() + ".userId";

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final UserService userService;

    /** 이 요청에서 이미 인증했다면 그 결과를, 아니면 헤더 자격증명을 검증해 userId 를 반환한다. */
    public Long resolveUserId(HttpServletRequest request) {
        if (request.getAttribute(USER_ID_ATTRIBUTE) instanceof Long cached) {
            return cached;
        }
        String loginId = request.getHeader(HEADER_LOGIN_ID);
        String password = request.getHeader(HEADER_LOGIN_PW);
        if (loginId == null || loginId.isBlank() || password == null || password.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "필수 로그인 헤더(X-Loopers-LoginId, X-Loopers-LoginPw)가 누락되었습니다.");
        }
        Long userId = userService.authenticate(new UserCommand.Authenticate(loginId, password)).getId();
        request.setAttribute(USER_ID_ATTRIBUTE, userId);
        return userId;
    }
}
