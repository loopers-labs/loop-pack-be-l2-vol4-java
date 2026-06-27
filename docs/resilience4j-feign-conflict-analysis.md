# Resilience4j 적용 전 FeignClient 충돌 분석

## 현재 FeignClient 재시도 설정 (`PgFeignClientConfig`)

```java
// 3회 시도 (초기 1회 + 재시도 2회), 100ms ~ 500ms 지수 백오프
public Retryer retryer() {
    return new Retryer.Default(100, 500, 3);
}

// 5xx → RetryableException 변환 → Feign Retryer 트리거
public ErrorDecoder errorDecoder() {
    return (methodKey, response) -> {
        if (response.status() >= 500) {
            return new RetryableException(...);
        }
        return FeignException.errorStatus(methodKey, response);
    };
}
```

| 항목 | 값 |
|------|-----|
| 총 시도 횟수 | 3회 (초기 1 + 재시도 2) |
| 초기 대기 | 100ms |
| 최대 대기 | 500ms |
| 재시도 트리거 | 5xx HTTP 상태 코드 |
| Connection Timeout | 3s |
| Read Timeout | 5s |

대상 엔드포인트:
- `POST /api/v1/payments` — 결제 요청 (멱등하지 않음)
- `GET /api/v1/payments/{transactionKey}` — 결제 상태 조회 (멱등)

---

## Resilience4j 적용 시 충돌 지점

### 1. Retry 중첩 (가장 심각)

Feign Retryer와 Resilience4j Retry를 함께 적용하면 호출 횟수가 곱셈으로 증가한다.

```
Feign 3회 × Resilience4j 3회 = 최대 9회 호출
```

`POST /api/v1/payments`는 멱등하지 않으므로 **중복 결제 요청** 위험이 있다.
`PaymentFacade`에서 FeignException을 잡는 시점에 이미 Feign은 내부적으로 3번 시도한 상태이며, Resilience4j가 그 위에서 다시 재시도하면 최대 9번의 결제 요청이 발생한다.

### 2. CircuitBreaker 실패 카운팅 왜곡

Feign Retryer와 CircuitBreaker의 중첩 위치에 따라 실패 카운팅 결과가 달라진다.

| 구조 | CircuitBreaker 실패 카운트 |
|------|--------------------------|
| CircuitBreaker → Feign Retryer (Feign이 안쪽) | Feign이 3번 재시도 후 최종 실패를 1번만 기록 → Circuit이 예상보다 늦게 열림 |
| Feign Retryer → CircuitBreaker (CB가 안쪽) | 재시도 3번 각각을 실패로 기록 → Circuit이 예상보다 빨리 열림 |

### 3. TimeLimiter와 Feign Timeout 중첩

Resilience4j TimeLimiter를 짧게 설정하면, Feign Read Timeout(5s) × 3회 = 최대 15s 동안 실행되는 Feign이 중간에 강제 종료된다.
TimeLimiter는 별도 스레드에서 타임아웃을 적용하므로, Feign 내부 스레드는 계속 실행 중인 상태로 리소스가 낭비된다.

```
TimeLimiter 5s 설정 시 → Feign 1회(5s) 완료 직후 타임아웃 → 2, 3회 시도 불가
```

---

## Alternatives Considered

| 옵션 | Pros | Cons |
|------|------|------|
| A — Feign Retryer 유지, Resilience4j는 CB/TimeLimiter만 적용 | 기존 카오스 테스트 그대로 유지, 추가 재시도 설정 불필요 | CB 실패 카운팅 왜곡 (Feign 3회 후 최종 실패 1건만 CB에 기록 → 서킷이 예상보다 늦게 열림), 재시도 메트릭 없음, 재시도 제어가 FeignClient 단위로만 가능 |
| B — Feign Retryer 유지 + Resilience4j Retry 중첩 적용 | 기존 코드 변경 최소화 | 최대 9회 호출 (3 × 3), CB 실패 카운팅 왜곡, 재시도 전략이 두 레이어에 분산되어 관리 어려움 |
| **선택: C — Feign Retryer 제거, Resilience4j Retry로 일원화** | 재시도 전략 단일화, CB 실패 카운팅 정확, Micrometer 연동 메트릭 자동 수집, 인스턴스별 세밀한 제어 가능 | 기존 카오스 테스트 재작성 필요 |

**선택 근거:**
A/B 모두 Feign Retryer와 Resilience4j가 공존하여 CB 실패 카운팅이 왜곡된다. 특히 B는 최대 9회 호출로 PG 부하 위험이 있다. C는 재시도 책임을 Resilience4j 하나로 일원화하여 CB와 자연스럽게 조합되고, Micrometer를 통해 재시도 횟수·성공률을 관찰할 수 있다.

---

## 관련 파일

| 파일 | 역할 |
|------|------|
| `infrastructure/pg/PgFeignClientConfig.java` | Retryer, ErrorDecoder, Timeout 설정 |
| `infrastructure/pg/PgFeignClient.java` | FeignClient 선언 |
| `application/payment/PaymentFacade.java` | FeignException → CoreException 변환 |
| `application/payment/PaymentPollingScheduler.java` | 상태 조회 폴링 (`GET` 엔드포인트 사용) |
| `test/infrastructure/pg/PgFeignClientChaosTest.java` | 재시도 카오스 테스트 |
