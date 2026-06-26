package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRequestException;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.util.Optional;

/**
 * pg-simulator 호출 어댑터({@link PaymentGateway} 구현).
 *
 * <p>PG 는 {@code X-USER-ID} 헤더로 사용자를 식별하므로 조회 시 동일한 userId 를 전달해야 한다.</p>
 *
 * <p>Resilience: 두 호출 모두 {@code @Retry → @CircuitBreaker} 순으로 감싼다(Retry 가 바깥).
 * timeout(RestClient 전송레벨)이 "한 호출을 빨리 끊는" 도구라면, CB 는 PG 가 망가졌을 때 "호출 자체를 중단"해
 * 스레드 고갈·장애 확산을 막고 slow-call 을 감지한다. Retry 는 그 사이에서 <b>안전한 실패만</b>(5xx·연결거부)
 * 짧게 재시도한다({@link PgRetryPredicate}) — read 타임아웃(in-doubt)·4xx·CB-open 은 재시도하지 않는다.</p>
 *
 * <p>fallback 은 바깥 {@code @Retry} 에 둔다: CB 에 두면 CB 가 예외를 먼저 복구해 버려 Retry 가 재시도할 기회를
 * 잃기 때문이다. 재시도까지 소진된 실패와 CB-open({@code CallNotPermittedException})은 모두 fallback 한 곳으로
 * 수렴해 {@link ErrorType#SERVICE_UNAVAILABLE} 단일 예외로 표면화된다. (CB 는 fallback 없이 실패를 집계만 하고
 * 그대로 전파한다.)</p>
 *
 * <p>호출자별 정책 차이: {@code request}(사용자 흐름)의 fallback 예외는 응용 서비스가 삼켜 PENDING 을 유지하고,
 * {@code find}(운영자 수동 정산)의 fallback 예외는 그대로 전파돼 503 으로 "PG 다운"을 알린다.
 * 특히 {@code find} 의 fallback 은 절대 {@code empty} 를 돌려주지 않는다 — {@code empty} 는 이미
 * "PG 에 해당 키 없음(404)" 의 의미라 CB-open 과 뭉개면 정산이 오판하기 때문이다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PgPaymentGateway implements PaymentGateway {

    private static final String USER_ID_HEADER = "X-USER-ID";
    private static final String CB_NAME = "pg";
    private static final int PG_ORDER_ID_MIN_LENGTH = 6;

    private final RestClient pgRestClient;

    @Override
    @Retry(name = CB_NAME, fallbackMethod = "requestFallback")
    @CircuitBreaker(name = CB_NAME)
    public Result request(Command command) {
        PgPaymentDto.Request body = new PgPaymentDto.Request(
                toPgOrderId(command.orderId()),
                command.cardType().name(),
                command.cardNo(),
                command.amount(),
                command.callbackUrl()
        );

        PgPaymentDto.Envelope<PgPaymentDto.TransactionResponse> response = pgRestClient.post()
                .uri("/api/v1/payments")
                .header(USER_ID_HEADER, command.userId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        PgPaymentDto.TransactionResponse data = requireData(response);
        return new Result(data.transactionKey(), data.toStatus(), data.reason());
    }

    @Override
    @Retry(name = CB_NAME, fallbackMethod = "findFallback")
    @CircuitBreaker(name = CB_NAME)
    public Optional<Result> find(String transactionKey, String userId) {
        try {
            PgPaymentDto.Envelope<PgPaymentDto.TransactionDetailResponse> response = pgRestClient.get()
                    .uri("/api/v1/payments/{transactionKey}", transactionKey)
                    .header(USER_ID_HEADER, userId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            PgPaymentDto.TransactionDetailResponse data = requireData(response);
            return Optional.of(new Result(data.transactionKey(), data.toStatus(), data.reason()));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * 결제 요청 fallback. 모든 실패·CB-open 이 여기로 수렴한다. 실패를 in-doubt(청구 여부 모름)와
     * not-in-doubt(청구 없음 확실)로 갈라 {@link PaymentRequestException} 으로 표면화하면, 응용은 그 한 비트만
     * 보고 PENDING 유지(in-doubt) / FAILED 확정(not-in-doubt)을 가른다.
     */
    @SuppressWarnings("unused")
    private Result requestFallback(Command command, Throwable t) {
        PaymentRequestException ex = classifyRequestFailure(t);
        if (ex.isInDoubt()) {
            log.warn("PG 결제요청 불가(in-doubt) — PENDING 유지 대상. orderId={}, cause={}", command.orderId(), t.toString());
        } else {
            log.warn("PG 결제요청 실패(청구 없음 확실) — FAILED 확정 대상. orderId={}, cause={}", command.orderId(), t.toString());
        }
        throw ex;
    }

    /**
     * 저수준 PG 실패를 in-doubt 여부로 분류한다(순수 함수 — 부수효과 없음, 단위테스트 대상).
     *
     * <p>not-in-doubt(청구가 일어날 수 없음이 확실)로 적극 식별되는 것만 FAILED 로 보낸다:
     * <ul>
     *   <li><b>4xx</b>({@link HttpClientErrorException}): 요청이 PG 에 닿았으나 거절됨 → 청구 없음.</li>
     *   <li><b>CB-open</b>({@link CallNotPermittedException}): 호출 자체를 차단 → 요청이 나가지 않음.</li>
     *   <li><b>연결 거부</b>({@link ResourceAccessException} cause {@link ConnectException}): TCP 연결 실패
     *       → 요청이 PG 에 닿지 못함.</li>
     * </ul>
     *
     * <p>나머지는 모두 in-doubt 로 보수적으로 둔다 — 특히 <b>connect/read 타임아웃</b>({@code SocketTimeoutException})은
     * 둘 다 같은 예외 타입이라 구분이 불가하고, read 타임아웃은 요청이 닿아 청구됐을 수 있으므로(in-doubt) 함부로
     * 실패로 단정하지 않는다. 5xx 도 일반적으로 부수효과 가능성이 있어 in-doubt 로 둔다.</p>
     */
    static PaymentRequestException classifyRequestFailure(Throwable t) {
        if (t instanceof HttpClientErrorException ce) {
            return PaymentRequestException.notInDoubt(
                    "PG 가 결제 요청을 거절했습니다. (status=" + ce.getStatusCode().value() + ")");
        }
        if (t instanceof CallNotPermittedException) {
            return PaymentRequestException.notInDoubt("결제 시스템 장애로 결제할 수 없습니다.");
        }
        if (t instanceof ResourceAccessException && t.getCause() instanceof ConnectException) {
            return PaymentRequestException.notInDoubt("결제 시스템에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
        return PaymentRequestException.inDoubt("결제 시스템이 일시적으로 응답하지 않습니다.");
    }

    /**
     * 결제 조회 fallback. {@code empty}(=404, 키 없음)와 혼동되지 않도록 반드시 예외로 표면화한다.
     * 수동 정산 호출자(운영자)는 503 으로 "PG 다운"을 인지하고 재시도한다.
     */
    @SuppressWarnings("unused")
    private Optional<Result> findFallback(String transactionKey, String userId, Throwable t) {
        log.warn("PG 결제조회 불가(CB/실패). transactionKey={}, cause={}", transactionKey, t.toString());
        throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, "결제 시스템이 일시적으로 응답하지 않습니다.");
    }

    /**
     * pg-simulator 계약: orderId 는 6자 이상 문자열이어야 한다(미만이면 400). 내부 주문 PK(예: {@code "1"})를
     * 그대로 보내면 거절되므로 0 패딩으로 최소 길이를 보장한다(이미 6자 이상이면 그대로). 콜백 echo 도 같은 포맷이라
     * 역상관(orderId → Long)이 필요하면 {@code Long.parseLong} 으로 복원할 수 있다.
     */
    private static String toPgOrderId(String orderId) {
        if (orderId.length() >= PG_ORDER_ID_MIN_LENGTH) {
            return orderId;
        }
        return "0".repeat(PG_ORDER_ID_MIN_LENGTH - orderId.length()) + orderId;
    }

    private static <T> T requireData(PgPaymentDto.Envelope<T> response) {
        if (response == null || response.data() == null) {
            throw new IllegalStateException("PG 응답 본문(data)이 비어 있습니다.");
        }
        return response.data();
    }
}
