package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 주문 API 진입 관문. 대기열을 통과해 입장 토큰을 받은 유저만 통과시킨다.
 * 인터셉터는 @LoginUser(ArgumentResolver) 보다 먼저 실행되므로, 로그인 ID 헤더로 userId 만 조회해
 * 저장된 토큰과 헤더 토큰을 비교한다. 비밀번호 검증은 이후 @LoginUser 가 수행한다.
 */
@RequiredArgsConstructor
@Component
public class EntryTokenInterceptor implements HandlerInterceptor {

    private static final String LOGIN_ID_HEADER = "X-Loopers-LoginId";
    private static final String ENTRY_TOKEN_HEADER = "X-Entry-Token";

    private final UserRepository userRepository;
    private final EntryTokenRepository entryTokenRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String loginId = header(request, LOGIN_ID_HEADER);
        if (loginId == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }
        String headerToken = header(request, ENTRY_TOKEN_HEADER);
        if (headerToken == null) {
            throw new CoreException(ErrorType.FORBIDDEN, "입장 토큰이 필요합니다.");
        }

        Long userId = userRepository.findByLoginId(new LoginId(loginId))
            .map(User::getId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));

        String storedToken;
        try {
            storedToken = entryTokenRepository.find(userId).orElse(null);
        } catch (DataAccessException e) {
            // 대기열은 fail-closed — Redis 에러 시 우회 진입을 막는다.
            throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "일시적으로 주문을 받을 수 없습니다. 잠시 후 다시 시도해주세요.");
        }

        if (!headerToken.equals(storedToken)) {
            throw new CoreException(ErrorType.FORBIDDEN, "유효한 입장 토큰이 아닙니다.");
        }
        return true;
    }

    private String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return (value == null || value.isBlank()) ? null : value;
    }
}
