# Failure-Ready Systems

> 외부 시스템(PG) 연동 과정에서 발생하는 장애와 지연에 대응하는 회복 전략 정리.
> Timeout → Retry → Circuit Breaker 순으로 방어 계층을 구성한다.

---

## 왜 필요한가

외부 시스템이 느려지거나 멈추면 요청을 기다리는 스레드가 점유 상태로 남는다.
이런 요청이 수십~수백 개 누적되면 애플리케이션 전체가 마비된다.
**장애는 외부에서 시작해 내부로 전파된다.** 이를 막는 것이 핵심이다.

---

## 1. Timeout

**가장 기본적인 방어선.** 일정 시간 내 응답이 없으면 실패로 간주하고 종료한다.

> 대부분의 실무 장애는 실패보다 **지연**에서 시작된다.

### 설정 예시

**HTTP (Feign Client)**
```java
@Configuration
public class FeignClientTimeoutConfig {
    @Bean
    public Request.Options feignOptions() {
        // connectTimeout: 1s, readTimeout: 3s
        return new Request.Options(1000, 3000);
    }
}

@FeignClient(name = "pgClient", url = "https://pg.example.com",
             configuration = FeignClientTimeoutConfig.class)
public interface PgClient {
    @PostMapping("/pay")
    PaymentResponse requestPayment(@RequestBody PaymentRequest request);
}
```

**JPA (HikariCP)**
```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 3000   # 커넥션 풀 대기 최대 시간
      validation-timeout: 2000   # 유효성 검사 제한
```

**Redis (Lettuce)**
```yaml
spring:
  data:
    redis:
      timeout: 3000              # 명령 실행 제한 시간
```

### 실무 포인트
- Feign: `connectTimeout` (TCP 연결)과 `readTimeout` (응답 수신)을 분리해서 설정한다.
- HikariCP: `connection-timeout` 없으면 커넥션 풀 고갈 시 무기한 대기한다.
- Lettuce: `commandTimeout` 없으면 Redis 장애 시 스레드가 무한 대기한다.
- 일반적으로 **2~5초** 범위. 기능 특성(결제 vs 조회)과 SLA에 따라 조절.

---

## 2. Retry

**일시적 장애(transient fault)에 대한 회복 전략.** 네트워크 순단, 서버 과부하(503) 등 단순 재시도로 해결 가능한 케이스가 많다.

### 주의사항
- 재시도 간 **backoff(대기 시간)** 필수. 없으면 실패한 서버에 폭발적 재요청 → DoS처럼 동작.
- **최대 시도 횟수 제한** 필수. 무한 재시도는 더 큰 장애 유발.
- Timeout과 조합: "3초 안에 3번까지만" 같은 복합 제어가 실무 기준.
- 재시도 끝까지 실패 시 **fallback으로 이어지도록** 설계.

### Resilience4j Retry

**의존성**
```groovy
dependencies {
    implementation "io.github.resilience4j:resilience4j-spring-boot3"
    implementation "org.springframework.boot:spring-boot-starter-aop"
}
```

**yml 설정**
```yaml
resilience4j:
  retry:
    instances:
      pgRetry:
        max-attempts: 3           # 최대 3회 시도
        wait-duration: 1s         # 재시도 간 1초 대기
        retry-exceptions:
          - feign.RetryableException
          - java.net.SocketTimeoutException
        fail-after-max-attempts: true  # 최종 실패 시 fallback 트리거
```

**적용**
```java
@Retry(name = "pgRetry", fallbackMethod = "fallback")
public PaymentResponse requestPayment(PaymentRequest request) {
    return pgClient.requestPayment(request);
}

public PaymentResponse fallback(PaymentRequest request, Throwable t) {
    // 결제 대기 상태로 처리하거나, 에러 응답 반환
    return new PaymentResponse("결제 대기 상태", false);
}
```

### 실무 포인트
- 재시도할 예외를 **명시적으로 지정**한다. 비즈니스 예외(400, 401)는 재시도 대상이 아님.
- `exponential backoff`: 1s → 2s → 4s 처럼 간격을 늘려 서버 부하 분산.
- `random backoff`: 여러 인스턴스가 동시에 재시도하는 thundering herd 방지.
- `fail-after-max-attempts: true` 설정으로 fallback과 자연스럽게 연결.

---

## 3. Circuit Breaker

**반복 실패 시 호출 자체를 차단하는 전략.** 계속 실패하는 외부 시스템에 요청을 보내는 것 자체가 낭비이고 장애를 심화시킨다.

### 상태 전환

```
       [실패율 >= 임계값]          [대기 시간 경과]
Closed ──────────────────→ Open ─────────────────→ Half-Open
  ↑                                                     │
  │         [테스트 요청 성공]                           │
  └─────────────────────────────────────────────────────┘
               [테스트 요청 실패 → 다시 Open]
```

| 상태 | 동작 |
|---|---|
| **Closed** | 정상. 모든 요청 통과. 실패 통계 수집 |
| **Open** | 차단. 모든 요청 즉시 fallback으로 전환 |
| **Half-Open** | 제한적 통과. 성공 시 Closed, 실패 시 Open으로 복귀 |

### Resilience4j Circuit Breaker

**yml 설정**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      pgCircuit:
        sliding-window-size: 10                        # 최근 10건 기준
        failure-rate-threshold: 50                     # 50% 이상 실패 시 Open
        wait-duration-in-open-state: 10s               # 10초 후 Half-Open 시도
        permitted-number-of-calls-in-half-open-state: 2
        slow-call-duration-threshold: 2s               # 2초 초과 응답 = 느린 호출
        slow-call-rate-threshold: 50                   # 느린 호출도 50% 이상이면 Open
```

**적용**
```java
@CircuitBreaker(name = "pgCircuit", fallbackMethod = "fallback")
public PaymentResponse requestPayment(PaymentRequest request) {
    return pgClient.requestPayment(request);
}

public PaymentResponse fallback(PaymentRequest request, Throwable t) {
    return new PaymentResponse("결제 대기 상태", false);
}
```

### 실무 포인트
- **느린 응답도 실패로 간주**: `slow-call-duration-threshold` + `slow-call-rate-threshold` 필수.
  - 타임아웃 없이 CB만 걸면 느린 요청이 쌓이다 뒤늦게 Open될 수 있음.
- Half-Open에서 `permitted-number-of-calls`를 너무 크게 잡으면 회복 중에 또 실패.
- fallback에서 **현재 시스템이 할 수 있는 최선**을 정의 (큐 적재, 임시 응답, 사용자 안내 등).

---

## Retry + Circuit Breaker 조합

두 전략은 **보완 관계**다.

```
요청
 │
 ▼
[Retry] ── 일시적 실패 → 재시도 (backoff 포함)
 │
 │ 재시도 모두 실패
 ▼
[Circuit Breaker] ── 실패 누적 → Open → 호출 차단
 │
 ▼
[Fallback] ── 사용자에게 대체 응답 제공
```

- Retry가 **일시적 실패를 흡수**한다.
- Circuit Breaker가 **지속적 실패를 감지하고 차단**한다.
- Fallback이 **사용자 영향을 최소화**한다.

**Resilience4j 어노테이션 조합**
```java
@CircuitBreaker(name = "pgCircuit", fallbackMethod = "fallback")
@Retry(name = "pgRetry")
public PaymentResponse requestPayment(PaymentRequest request) {
    return pgClient.requestPayment(request);
}
```

> 적용 순서: `@Retry` → `@CircuitBreaker` (내부에서 외부 순으로 감싸진다)

---

## 전략 선택 가이드

| 상황 | 적용 전략 |
|---|---|
| PG 응답이 가끔 늦음 | Timeout |
| PG가 가끔 503 반환 | Retry (with backoff) |
| PG가 장시간 완전히 죽음 | Circuit Breaker |
| 위 상황 모두 | Timeout + Retry + Circuit Breaker + Fallback |

---

## References

| 구분 | 링크 |
|---|---|
| Resilience4j 공식 | https://resilience4j.readme.io/docs/getting-started |
| Resilience4j with Spring Boot | https://www.baeldung.com/spring-boot-resilience4j |
| FeignClient Timeout | https://www.baeldung.com/feign-timeout |
| Spring Cloud Feign | https://cloud.spring.io/spring-cloud-netflix/multi/multi_spring-cloud-feign.html |
| Fallback Pattern (MSA) | https://badia-kharroubi.gitbooks.io/microservices-architecture/content/patterns/communication-patterns/fallback-pattern.html |
