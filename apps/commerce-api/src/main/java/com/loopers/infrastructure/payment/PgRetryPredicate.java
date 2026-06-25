package com.loopers.infrastructure.payment;

import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.util.function.Predicate;

/**
 * PG 호출 재시도 여부 판별. "안전하다고 적극적으로 식별되는 것"만 재시도하고, 애매하면 재시도하지 않는다.
 *
 * <p>재시도 O — 둘 다 <b>빠르게 실패</b>해 재시도 비용이 싸다:
 * <ul>
 *   <li><b>5xx</b>({@link HttpServerErrorException}): pg-simulator 의 동기 경로는 외부 비가역 효과가 없어
 *       500 이 곧 깨끗한 롤백이다(부수효과 없음 → 재시도 안전).</li>
 *   <li><b>연결 거부</b>({@link ResourceAccessException} cause {@link ConnectException}): 요청이 PG 에
 *       닿지조차 못했으므로 결제가 일어났을 수 없다(not in-doubt).</li>
 * </ul>
 *
 * <p>재시도 X — 위험하거나 무의미:
 * <ul>
 *   <li><b>read 타임아웃</b>({@code SocketTimeoutException}): 요청은 닿았는데 응답만 못 받은 in-doubt
 *       상태라 재시도 시 중복 가능. (connect-timeout 도 같은 예외 타입이라 구분 불가 → 보수적으로 제외)</li>
 *   <li><b>4xx</b>: 결정론적 실패라 재시도해도 같은 답.</li>
 *   <li><b>CallNotPermitted</b>(CB OPEN): 차단된 호출을 또 두드리지 않고 즉시 fallback(fail-fast).</li>
 * </ul>
 */
public class PgRetryPredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable t) {
        if (t instanceof HttpServerErrorException) {
            return true; // 5xx
        }
        if (t instanceof ResourceAccessException) {
            return t.getCause() instanceof ConnectException; // 연결 거부만, read/connect 타임아웃은 제외
        }
        return false;
    }
}
