# 6주차 — Resilience 전략 (Timeout / CircuitBreaker / Retry / Fallback)

## 1. PG 시뮬레이터의 동작 특성 (주어진 명세)

- 요청 성공 확률: 60%
- 요청 지연: 100ms ~ 500ms
- 처리 지연: 1s ~ 5s
- 처리 결과: 성공 70% / 한도 초과 20% / 잘못된 카드 10%

## 2. Timeout 기준

| 레이어 | 값 | 근거 |
|--------|----|------|
| Feign `connect` timeout | 500ms | 요청 지연 상한 |
| Feign `read` timeout | 1500ms | 요청 지연 상한(500ms) × 3 — 짧은 네트워크 흔들림 흡수 |
| Resilience4j slow-call threshold | 2s | 정상 응답 (요청 지연 100~500ms) 의 4배 — 회로 차단의 "느림" 기준 |

처리 지연 (1~5s) 은 의도적으로 우리쪽 타임아웃에 안 맞춘다 — PG 는 비동기 처리라 우리는 **처리 완료를 기다리지 않는다**. transactionKey 만 받고 즉시 응답, 처리 결과는 콜백/복구 폴링이 책임.

## 3. Circuit Breaker

```yaml
resilience4j.circuitbreaker.instances.pg:
  sliding-window-type: COUNT_BASED
  sliding-window-size: 20
  minimum-number-of-calls: 10
  failure-rate-threshold: 50
  slow-call-rate-threshold: 50
  slow-call-duration-threshold: 2s
  wait-duration-in-open-state: 10s
  permitted-number-of-calls-in-half-open-state: 3
  automatic-transition-from-open-to-half-open-enabled: true
```

| 항목 | 값 | 의도 |
|------|----|------|
| sliding window | COUNT_BASED, 20 | 최근 20건 기준 |
| minimum calls | 10 | 트래픽 낮을 때 false-positive 회로 차단 방지 |
| failure rate | 50% | PG 정상 성공률이 60% 라 50% 미만이면 진짜 장애로 판단 |
| slow call rate | 50% | 느림이 절반이면 차단 — 폴백으로 분기 |
| wait open | 10s | 10초 후 half-open 으로 시도 |
| half-open calls | 3 | 3건만 시험 — 3건 다 성공해야 closed 복귀 |

## 4. Retry

```yaml
resilience4j.retry.instances.pg:
  max-attempts: 3
  wait-duration: 200ms
  exponential-backoff-multiplier: 2
  retry-exceptions:
    - feign.RetryableException
    - java.io.IOException
    - org.springframework.web.client.ResourceAccessException
```

- `IOException` 류만 재시도 — 5xx/타임아웃 등 진짜 네트워크 흔들림 가정
- 비즈니스 실패 (한도 초과, 잘못된 카드) 는 재시도 안 함 — 멱등하지만 의미 없음
- 지수 백오프: 200ms → 400ms → 800ms

**중복 실행 우려**: PG 시뮬레이터는 같은 `orderId` 에 대해 같은 거래를 반환한다고 명세. 그래서 POST 재시도가 안전.

## 5. Fallback (가장 중요)

`request` 메서드의 폴백은 단순 에러가 아니라 **상태 복구 가능한 형태로 변환**한다:

```java
private PgRequestResponse requestFallback(PgRequestCommand command, Throwable t) {
    throw new PgRequestException("PG 결제 요청이 지연/실패하여 복구 대기 상태로 전환합니다.", t);
}
```

Facade 는 `PgRequestException` 을 받으면 결제를 `TIMEOUT_PENDING` 으로 보내고 복구 스케줄러가 마무리한다.

| 메서드 | 폴백 동작 |
|--------|---------|
| `request` | `PgRequestException` 발생 → Facade 가 TIMEOUT_PENDING 으로 전이 |
| `getByTransactionKey` | `Optional.empty()` 반환 → 복구 스케줄러는 다음 주기에 재시도 |
| `findByOrderId` | 빈 리스트 반환 → 동일 |

## 6. 복구 (Recovery)

```
@Scheduled(fixedDelay = 5s)
recoverRecoverablePayments():
  대상 = TIMEOUT_PENDING 전부 +
        (REQUESTED 인데 마지막 갱신이 30s 이전 — 콜백 유실 의심)
  for each:
    if transactionKey 없음:
      PG findByOrderId → 발견 시 attach → 상태 동기화
    else:
      PG getByTransactionKey → 종료 상태면 markSucceeded/markFailed
```

복구 주기 = 5초. 처리 지연 최대 5초보다 (의도적으로) 작게 두지 않음 — 자연 만료 후 충분히 결과 확정된 뒤 폴링.

## 7. 외부 시스템 장애 시 우리 시스템이 무너지지 않도록

| 시나리오 | 우리쪽 응답 | 데이터 상태 |
|---------|-----------|-----------|
| PG 가 5xx 만 반환 | 200 OK + status=TIMEOUT_PENDING | 결제 PENDING → TIMEOUT_PENDING |
| PG 가 5초 이상 응답 안 함 | 200 OK + status=TIMEOUT_PENDING | 동일 |
| PG 회로 차단 (수많은 실패) | 200 OK + status=TIMEOUT_PENDING (즉시 폴백) | 동일 |
| PG 전체 다운 | 200 OK + status=TIMEOUT_PENDING | 복구 스케줄러도 빈 응답 → 다음 주기 |
| 콜백 유실 | (콜백이 안 옴) | 복구 스케줄러가 단건 GET 으로 동기화 |

## 8. 의도적으로 *하지 않은* 것

| 미적용 | 이유 |
|--------|------|
| Bulkhead (스레드 풀 분리) | PG 단일 외부라 의미 작음. 외부 시스템 추가 시 도입 |
| Outbox 패턴 | 본 PR 의 트랜잭션 분리 + 복구 스케줄러 조합으로 충분. 이벤트 발행으로 확장 시 도입 |
| HMAC 콜백 서명 검증 | 시뮬레이터엔 서명 명세 없음. 운영 단계 보안 강화 시 추가 |
| 분산 락 (복구 중복 방지) | 단일 인스턴스 가정. 다중 인스턴스 운영 시 ShedLock 등 검토 |
