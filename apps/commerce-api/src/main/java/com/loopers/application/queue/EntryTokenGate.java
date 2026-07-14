package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 주문 진입 게이트 — 행사(트래픽 폭증) 때만 켜는 스위치(queue.order-gate.enabled, 기본 off).
 * off 면 즉시 통과해 기존 주문 흐름이 그대로 유지되고, on 이면 대기열에서 발급한 입장 토큰을 검증한다.
 *
 * Graceful Degradation(가용성 우선): Redis 예외로 "검증 자체가 불가"하면 1회 즉시 재시도 후
 * fail-open(통과)한다 — 보호장치(대기열)가 죽었다고 매출(주문)까지 막지 않는다. 하류는 커넥션 풀+기존 락이
 * 자연 스로틀 역할을 한다. 단, "토큰 부재/불일치"는 정상 거부(4xx)로 엄격히 구분한다.
 * 전면 다운 시 자동 게이트 해제는 없다(순단 flapping 방지) — 운영자가 스위치를 수동 토글한다.
 */
@Component
public class EntryTokenGate {

    private static final Logger log = LoggerFactory.getLogger(EntryTokenGate.class);

    private final EntryTokenRepository entryTokenRepository;
    private final boolean enabled;

    public EntryTokenGate(EntryTokenRepository entryTokenRepository, QueueProperties properties) {
        this.entryTokenRepository = entryTokenRepository;
        this.enabled = properties.orderGate().enabled();
    }

    /**
     * 주문 진입 검증 — 발급된 토큰과 X-Loopers-EntryToken 헤더 값이 일치해야 통과.
     * 없거나 불일치(미진입·만료·위조)면 대기열 진입을 안내하며 거부한다.
     */
    public void verify(String loginId, String entryToken) {
        if (!enabled) {
            return;
        }
        Optional<String> issued;
        try {
            issued = findWithOneRetry(loginId);
        } catch (DataAccessException e) {
            // 검증 불가(Redis 장애) — fail-open 통과. DataAccessException 계열에 한정한다:
            // 버그성 예외(NPE 등)까지 통과시키면 안 되므로 consume 의 catch(Exception)과 범위가 다르다.
            log.warn("[queue] 대기열 게이트 검증 불가 — fail-open 통과 loginId={} : {}", loginId, e.toString());
            return;
        }
        // 도메인 검증 실패 컨벤션(BAD_REQUEST) 재사용 — 새 ErrorType 을 늘리지 않는다(스펙: 기존 ErrorType 재사용, 4xx).
        String token = issued.orElseThrow(this::rejection);
        if (entryToken == null || !token.equals(entryToken)) {
            throw rejection();
        }
    }

    /**
     * 주문 성공 시 1회용 토큰 소진(DEL) — best-effort. 어떤 예외도 완료된 주문을 되돌리면 안 되므로
     * verify 와 달리 catch(Exception)으로 전부 삼킨다(잔여 토큰은 TTL 이 정리).
     */
    public void consume(String loginId) {
        if (!enabled) {
            return;
        }
        try {
            entryTokenRepository.delete(loginId);
        } catch (Exception e) {
            log.warn("[queue] 입장 토큰 소진 실패(무시 — best-effort) loginId={} : {}", loginId, e.toString());
        }
    }

    /** Redis 순단 대비 1회 즉시 재시도 — 재실패는 호출부에서 fail-open 처리한다. */
    private Optional<String> findWithOneRetry(String loginId) {
        try {
            return entryTokenRepository.find(loginId);
        } catch (DataAccessException e) {
            log.warn("[queue] 게이트 토큰 조회 실패 — 1회 재시도 loginId={} : {}", loginId, e.toString());
            return entryTokenRepository.find(loginId);
        }
    }

    private CoreException rejection() {
        return new CoreException(ErrorType.BAD_REQUEST,
            "입장 토큰이 유효하지 않습니다. 대기열에 진입한 뒤 다시 시도해주세요.");
    }
}
