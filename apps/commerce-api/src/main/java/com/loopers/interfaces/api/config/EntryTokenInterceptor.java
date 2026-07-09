package com.loopers.interfaces.api.config;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.interfaces.api.auth.HeaderAuthenticator;
import com.loopers.support.config.QueueProperties;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.UUID;

/**
 * 주문 생성({@code POST /api/v1/orders}) 앞단의 대기열 관문. 주문 도메인·응용 레이어는
 * 대기열의 존재를 모른다 — 트래픽 제어는 유스케이스가 아니라 진입 지점의 관심사다.
 *
 * <p>검증 순서: 게이트 off / POST 아님 → 통과, 토큰 헤더 부재 → 인증 조회 없이 즉시 403
 * (DB 를 보호하려는 관문이 무자격 요청에 인증 쿼리를 써 주지 않도록 fail-cheap-first),
 * 이후 인증 → 발급 토큰과 대조. 응답 코드는 403 — 인증 실패(401)가 아니라 "인증은 됐지만
 * 입장 권한이 없음"이다.</p>
 *
 * <p>토큰 소진은 {@code afterCompletion} 에서 응답이 2xx 일 때만 — 주문이 실패(재고 부족,
 * 검증 오류 등)한 유저의 토큰을 소진시키면 대기열을 다시 서야 하므로, TTL 내 재시도를 허용한다.</p>
 *
 * <p><b>동시 주문 차단</b>: 토큰 검증(find+대조)은 아무것도 소진하지 않고 소진은 완료 후에 일어나므로,
 * 같은 토큰으로 동시에 들어온 여러 주문이 모두 검증을 통과할 수 있다(검사-사용 시점 분리). 이러면
 * "토큰 1장 = 주문 1건"이 깨져 발급 속도 = 유입 TPS 전제가 무너진다. 검증 통과 직후 유저당 in-flight
 * 마크를 {@code SETNX} 로 선점해, 처리 중인 주문이 있으면 뒤따르는 동시 주문을 409 로 막는다. 마크는
 * 완료 시 해제하고, 이 마크를 <b>선점한 요청만</b>이 해제·토큰 소진을 수행한다(거절된 동시 요청이 남의
 * 마크를 지우지 않도록 request attribute 로 소유권을 표시).</p>
 */
@RequiredArgsConstructor
@Component
public class EntryTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER_ENTRY_TOKEN = "X-Entry-Token";

    /** in-flight 마크 소유권 — 이 lease 가 있는 요청만 afterCompletion 에서 해제·소진한다(owner token 대조 포함). */
    private static final String ATTR_IN_FLIGHT_LEASE = EntryTokenInterceptor.class.getName() + ".IN_FLIGHT_LEASE";

    /** in-flight 마크 TTL — 정상 해제 누락(크래시) 시의 자동 해제 안전망. 주문 처리 최대 시간(획득 타임아웃 3s)보다 넉넉히 크게. */
    private static final Duration IN_FLIGHT_TTL = Duration.ofSeconds(30);

    /** 이 요청이 선점한 in-flight 마크의 소유권 증표(userId 키 + owner token 값). */
    private record InFlightLease(Long userId, String ownerToken) {
    }

    private final QueueProperties queueProperties;
    private final EntryTokenRepository entryTokenRepository;
    private final HeaderAuthenticator headerAuthenticator;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (skip(request)) {
            return true;
        }
        String presented = request.getHeader(HEADER_ENTRY_TOKEN);
        if (presented == null || presented.isBlank()) {
            throw new CoreException(ErrorType.FORBIDDEN,
                    "입장 토큰이 없습니다. 대기열에 진입해 순번을 기다려 주세요.");
        }
        Long userId = headerAuthenticator.resolveUserId(request);
        String issued = entryTokenRepository.find(userId)
                .orElseThrow(() -> new CoreException(ErrorType.FORBIDDEN,
                        "입장 토큰이 만료되었거나 발급되지 않았습니다. 대기열에 다시 진입해 주세요."));
        if (!issued.equals(presented)) {
            throw new CoreException(ErrorType.FORBIDDEN, "입장 토큰이 유효하지 않습니다.");
        }
        String ownerToken = UUID.randomUUID().toString();
        if (!entryTokenRepository.acquireInFlight(userId, ownerToken, IN_FLIGHT_TTL)) {
            throw new CoreException(ErrorType.CONFLICT,
                    "이미 처리 중인 주문이 있습니다. 완료 후 다시 시도해 주세요.");
        }
        request.setAttribute(ATTR_IN_FLIGHT_LEASE, new InFlightLease(userId, ownerToken));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!(request.getAttribute(ATTR_IN_FLIGHT_LEASE) instanceof InFlightLease lease)) {
            return; // 마크를 선점하지 못한 요청(스킵·거절)은 남의 마크·토큰을 건드리지 않는다
        }
        try {
            int status = response.getStatus();
            if (status >= 200 && status < 300) {
                entryTokenRepository.delete(lease.userId()); // 성공(2xx)에만 토큰 소진
            }
        } finally {
            // 내가 선점한 마크만 해제(owner token 대조) — 성공·실패 무관, 실패면 토큰은 남아 재시도 가능
            entryTokenRepository.releaseInFlight(lease.userId(), lease.ownerToken());
        }
    }

    private boolean skip(HttpServletRequest request) {
        return !queueProperties.enabled() || !HttpMethod.POST.matches(request.getMethod());
    }
}
