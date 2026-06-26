---
date: 2026-06-26
type: plan
title: 결제 도메인 구현 계획 (Payment Implementation Plan)
description: payment-design.md 설계를 commerce-api 레이어드 아키텍처에 구현하기 위한 단계별 계획. 파일 목록, 레이어별 책임, Resilience 설정, 테스트 전략, Quest 체크리스트 매핑을 포함한다.
tags: [loopers, payment, implementation, resilience]
---

# 결제 도메인 구현 계획 (Payment Implementation Plan)

> 본 문서는 [[payment-design]]의 설계를 `commerce-api`의 레이어드 아키텍처(interfaces → application → domain → infrastructure)에 구현하기 위한 단계별 계획이다.
> 의존성은 이미 존재한다: `spring-cloud-starter-openfeign`, `resilience4j-spring-boot3` (build.gradle.kts).

---

## 0. 전제 — 패키지 레이어링 규약 준수

| 레이어 | 패키지 | 책임 |
|---|---|---|
| interfaces | `interfaces/api/payment` | `@RestController`, `*V1Dto(record)`, `*V1ApiSpec` |
| application | `application/payment` | `PaymentFacade(@Component)` — 주문·결제 조합, `*Info` 변환. **비트랜잭션** |
| domain | `domain/payment` | `PaymentModel(@Entity)`, `PaymentStatus`, `CardType`, `PaymentRepository`, `PaymentService(@Component)`, `PaymentGateway`(포트) |
| infrastructure | `infrastructure/payment` | `PaymentRepositoryImpl`, `PaymentJpaRepository`, `PaymentGatewayImpl(@Component, FeignClient 어댑터)`, Feign DTO |

- DI는 `@RequiredArgsConstructor` + `final`. `@Service` 대신 `@Component`.
- Repository 인터페이스는 domain, 구현은 infrastructure (헥사고날).
- **외부 PG 연동도 동일하게 포트(`PaymentGateway` 인터페이스, domain) + 어댑터(`PaymentGatewayImpl`, infrastructure)** 로 DIP 준수.

---

## 1. 도메인 레이어 (`domain/payment`)

### 1.1 `CardType` (enum)
```java
public enum CardType { SAMSUNG, KB, HYUNDAI }
```

### 1.2 `PaymentStatus` (enum) — 상태 머신
```java
public enum PaymentStatus {
    PENDING, PAID, FAILED, UNKNOWN;
    public boolean isTerminal() { return this == PAID || this == FAILED; }
}
```

### 1.3 `PaymentModel` (`@Entity extends BaseEntity`)
- 필드: `orderId(Long)`, `orderNumber(String)`, `userId(Long)`, `cardType`, `cardNo(마스킹)`, `amount(Long)`, `status`, `transactionKey(nullable)`, `failureReason(nullable)`.
- 정적 팩토리 `pending(...)`: 생성 시 `PENDING`. 자기 검증(카드번호 형식·amount>0 → `CoreException(BAD_REQUEST)`).
- 카드번호 마스킹 로직(`1234-****-****-1451`)은 모델 내부 또는 VO로.
- 전이 메서드: `markPaid(transactionKey)`, `markFailed(reason)`, `markUnknown()` — **terminal에서의 전이 시도는 무시(멱등) 또는 `CoreException`**. (실제 동시성 차단은 §3 조건부 UPDATE가 담당, 모델 메서드는 불변식 표현)
- `attachTransactionKey(key)`: 접수 응답 반영(PENDING 유지).

### 1.4 `PaymentRepository` (인터페이스, domain)
```java
public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);
    Optional<PaymentModel> findById(Long id);
    Optional<PaymentModel> findByTransactionKey(String transactionKey);

    // 요청 측 멱등성("따닥 클릭"): 해당 주문의 활성(PENDING/PAID) 결제 1건 조회.
    // pay() 진입 시 이미 있으면 PG를 새로 호출하지 않고 멱등 반환한다.
    Optional<PaymentModel> findActiveByOrderId(Long orderId); // status in (PENDING, PAID)

    // 동시성 처리: 조건부 UPDATE (check-then-act 갭 제거).
    // status='PENDING'인 행만 전이시키고 affected rows로 승자를 판별한다.
    // affected=1이면 호출자가 후처리를 1회 실행, 0이면 스킵한다.
    int transitionToPaid(Long id, String transactionKey);
    int transitionToFailed(Long id, String reason);
    int transitionToUnknown(Long id); // 격리도 조건부 UPDATE(PENDING 만) — 동시 전이 race 시 terminal 덮어쓰기 방지(§8)

    // 폴링 대상: grace period 경과한 PENDING (createdAt 기준)
    List<PaymentModel> findPendingOlderThan(ZonedDateTime threshold);
}
```

### 1.5 `PaymentService` (`@Component`)
| 메서드 | Tx | 책임 |
|---|---|---|
| `createPending(order, cardType, cardNo)` | **Tx1** `@Transactional` | Payment(PENDING) 저장 후 커밋 → 닻 확보 |
| `attachTransactionKey(paymentId, key)` | **Tx2** `@Transactional` | 접수 응답 반영 |
| `confirm(transactionKey, status, reason, amount, cardNo)` | **Tx3** `@Transactional` | **(1)** 대상 Payment 조회 후 `amount`·`cardNo` 무결성 가드(설계 §6.2) — 불일치 시 전이 거부 + `markUnknown` **(2)** 일치 시 조건부 UPDATE 전이 + affected=1일 때만 후처리 트리거 |
| `findPendingForReconcile(threshold)` | (조회) | 폴링 대상 조회 |
| `markUnknown(paymentId)` | `@Transactional` | 상한 초과 격리 |

### 1.6 `PaymentGateway` (포트, domain)
```java
public interface PaymentGateway {
    // 결제 요청 (CircuitBreaker:paymentRequest + Timeout + Retry 적용 지점)
    PgTransaction request(PgPaymentCommand command);
    // transactionKey로 단건 조회 (CircuitBreaker:paymentQuery)
    Optional<PgTransaction> findByTransactionKey(String transactionKey);
    // orderNumber로 조회 (닻 되짚기, CircuitBreaker:paymentQuery)
    List<PgTransaction> findByOrderId(String orderNumber);
}
```
`PgTransaction`(domain DTO): `transactionKey`, `status(PENDING/SUCCESS/FAILED)`, `reason`, `amount`(무결성 가드용 — PG 조회 응답이 제공하면 폴링 경로에서도 금액 대조). — **PG의 status를 우리 PaymentStatus로 매핑하는 책임은 어댑터/서비스에 둔다**(`SUCCESS→PAID`, `FAILED→FAILED`, `PENDING→`전이 보류). 무결성 가드(설계 §6.2)는 `amount`가 일치할 때만 전이를 허용한다.

### 1.7 예외 타입 — 재시도 정책과 직결 (중요)
```java
// 미도달(5xx·Connect Timeout 등 "돈 안 빠짐"이 증명되는 실패) → 자동 재시도 안전
public class PaymentGatewayException extends RuntimeException { ... }

// Read Timeout("요청 도달, 응답만 유실" 가능) → 자동 재시도 금지 대상.
// 상위 타입을 상속해 CB는 함께 집계하되, @Retry에서는 ignore-exceptions로 제외(§6).
public class PaymentGatewayTimeoutException extends PaymentGatewayException { ... }
```
> 어댑터(§2.4)가 Feign 예외를 이 두 타입으로 분기 변환한다. CB record-exceptions=상위 타입(둘 다 집계), Retry는 타임아웃 하위 타입을 ignore → "타임아웃 블라인드 재시도" 함정 회피(설계 §7.4).

---

## 2. 인프라 레이어 (`infrastructure/payment`)

### 2.1 `PaymentJpaRepository extends JpaRepository<PaymentModel, Long>`
- `findByTransactionKey`, `findAllByStatusAndCreatedAtBefore` (Spring Data 파생 쿼리).
- 조건부 UPDATE는 `@Modifying @Query`:
```java
// 동시성: status='PENDING'일 때만 전이. 영속성 컨텍스트 우회(@Modifying)이므로
// 후처리는 반드시 반환값(affected rows) 확인 후 재조회로 진행한다.
@Modifying(clearAutomatically = true)
@Query("UPDATE PaymentModel p SET p.status = 'PAID', p.transactionKey = :key, p.updatedAt = :now " +
       "WHERE p.id = :id AND p.status = 'PENDING'")
int transitionToPaid(@Param("id") Long id, @Param("key") String key, @Param("now") ZonedDateTime now);
```
> JPQL로 표현 가능하므로 nativeQuery 불필요. `@Modifying`은 영속성 컨텍스트를 우회하니 후처리 전 재조회 필수.

### 2.2 `PaymentRepositoryImpl (@Component implements PaymentRepository)`
- JpaRepository에 위임. 조건부 UPDATE의 affected rows를 그대로 반환.

### 2.3 `PaymentGatewayFeignClient` (FeignClient)
```java
@FeignClient(name = "pgSimulator", url = "${payment-gateway.url}", configuration = PaymentGatewayFeignConfig.class)
public interface PaymentGatewayFeignClient {
    @PostMapping("/api/v1/payments")
    PgApiResponse<PgTransactionResponse> request(@RequestHeader("X-USER-ID") String userId, @RequestBody PgPaymentRequest body);

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgApiResponse<PgTransactionDetailResponse> getByKey(@RequestHeader("X-USER-ID") String userId, @PathVariable String transactionKey);

    @GetMapping("/api/v1/payments")
    PgApiResponse<PgOrderResponse> getByOrderId(@RequestHeader("X-USER-ID") String userId, @RequestParam("orderId") String orderId);
}
```
- Feign DTO(`PgPaymentRequest`, `PgTransactionResponse`, `PgApiResponse<T>` 등)는 infrastructure에 둔다(pg-simulator 응답 래퍼 구조에 맞춤).
- **PG 요청 본문**: `orderId`(=우리 `orderNumber`), `cardType`, `cardNo`, `amount`(=`order.finalPrice`), `callbackUrl`.

### 2.4 `PaymentGatewayImpl (@Component implements PaymentGateway)`
- Feign 호출을 감싸고 `@CircuitBreaker(name="paymentRequest")`, `@Retry(name="paymentRequest")` 적용.
  - **`fallbackMethod`는 두지 않는다.** Fallback(PENDING "처리 중")은 §3.1 facade의 try-catch **한 곳**에서 처리한다. 어댑터에 `fallbackMethod`를 두면 resilience4j가 `CallNotPermittedException`/도메인 예외를 흡수해 버려 facade catch가 죽은 코드가 되고, 미도달엔 `transactionKey`가 없어 `PgTransaction` sentinel 합성도 어색하다. → **어댑터는 도메인 예외를 그대로 전파**하고, 이미 Tx1에서 PENDING을 저장해 둔 facade가 "key를 안 붙인다"만 결정한다.
- 조회 메서드엔 `@CircuitBreaker(name="paymentQuery")`.
- PG status → 도메인 매핑.
- **Feign 예외 → 도메인 예외 분기 변환(재시도 정책 분리)**:
  - 5xx(`FeignException` 5xx) · Connect Timeout(연결 실패) → `PaymentGatewayException` (미도달, **재시도 대상**)
  - Read Timeout(`SocketTimeoutException` read) → `PaymentGatewayTimeoutException` (**재시도 제외**, PENDING 유지 후 폴링)
  - **요청(POST) 4xx → `CoreException(BAD_REQUEST)`** (CB/Retry 모두 ignore)
  - **조회(GET) 404(`FeignException.NotFound`) → 빈 `Optional`/빈 `List`** (구현 보정). PG 의 `GET /{key}`·`GET ?orderId=` 는 "주문 없음"을 404 로 응답한다. 이를 예외로 흘리면 `paymentQuery` CB 오작동 + "주문 없음 → FAILED 확정" 로직이 죽으므로, 조회 메서드에서 404 는 **빈 결과(= 미도달 신호)** 로 변환한다. (4xx → CoreException 매핑은 요청 POST 에만 적용.)
- 위 분기는 어댑터 try-catch 에서 구현한다. `RetryableException` 은 `FeignException` 의 하위 타입이므로 **먼저 catch** 한다(타임아웃/연결 실패). FeignClient 자체 재시도(`Retryer`)는 `Retryer.NEVER_RETRY`로 끄고 resilience4j `@Retry`로 일원화한다.
- **`@FeignClient(primary = false)`** (구현 보정): Spring Cloud OpenFeign 은 클라이언트 빈을 기본 `@Primary` 로 등록한다. 통합 테스트에서 `@Primary` 스텁으로 교체하려면 `primary = false` 로 둬 충돌을 푼다(운영은 단일 빈이라 무영향).

---

## 3. 애플리케이션 레이어 (`application/payment`)

### 3.1 `PaymentFacade (@Component)` — **메서드 전체에 `@Transactional` 금지**
```java
public PaymentInfo pay(Long userId, String orderNumber, CardType cardType, String cardNo) {
    OrderModel order = orderService.getByOrderNumberAndValidateOwner(orderNumber, userId); // 검증

    // 요청 측 멱등성("따닥 클릭"): 이미 활성(PENDING/PAID) 결제가 있으면 PG 재호출 없이 멱등 반환
    Optional<PaymentModel> active = paymentService.findActive(order.getId());
    if (active.isPresent()) {
        return PaymentInfo.of(active.get()); // PENDING이면 "처리 중", PAID면 그 결과
    }

    PaymentModel payment = paymentService.createPending(order, cardType, cardNo);          // Tx1 (커밋)
    try {
        PgTransaction tx = paymentGateway.request(toCommand(order, payment, cardNo));       // 트랜잭션 밖 (CB/Timeout/Retry)
        paymentService.attachTransactionKey(payment.getId(), tx.transactionKey());         // Tx2
    } catch (PaymentGatewayException | CallNotPermittedException e) {
        // 미도달(5xx)·Read Timeout·CB OPEN 모두 PaymentGatewayException(또는 그 하위 타입)/CallNotPermitted로 수렴
        // → PENDING 유지. 폴링이 복구. 사용자에겐 "처리 중"
        log.warn("PG 요청 미확정, PENDING 유지: orderNumber={}", orderNumber, e);
    }
    return PaymentInfo.processing(payment); // status=PENDING "결제 처리 중"
}
```
- `OrderService`에 `getByOrderNumberAndValidateOwner` 추가 필요(현재 PK 조회만 존재).
- `PaymentService.findActive(orderId)`는 `findActiveByOrderId`(§1.4)에 위임. 정상 경로의 멱등성은 **진입 시 `findActive` 조회**로 보장한다.
- **DB 레벨 백스톱**: "활성 결제 1건" 부분 unique 제약(설계 §9.1)을 최후 방어선으로 둔다. 동시 진입 race를 제약으로 막으려면 `createPending`(Tx1) 둘레에 `catch (DataIntegrityViolationException) → findActive 재조회 → 멱등 반환`을 **추가로** 구현해야 한다(위 스켈레톤의 try-catch는 PG 예외만 다루므로 별도). 과제 범위에선 진입 시 `findActive`만으로 충분하고, 제약+재조회는 멀티 인스턴스 대비 강화 옵션이다.
- `PaymentInfo`(application DTO, record): `paymentId`, `orderNumber`, `status`, `message`.

### 3.2 후처리(주문 확정) — `confirm` 흐름 (구현 보정)
- `PaymentService.confirm(...)`은 무결성 가드 + 조건부 UPDATE 후 **`ConfirmOutcome`(PAID/FAILED/SKIPPED/ISOLATED/STILL_PENDING)** 을 반환한다(boolean 대신 enum 으로 후처리 분기를 명확화).
- **confirm(조건부 UPDATE) + 주문 후처리를 한 트랜잭션으로 묶는다.** `PaymentFacade.confirmResolved(...)` 에 `@Transactional` 을 붙여, `confirm` 이 반환한 outcome 이 `PAID`면 `orderService.markPaid(orderId)`, `FAILED`면 `markPaymentFailed(orderId)` 를 **같은 트랜잭션 안에서** 호출한다. 두 트랜잭션으로 쪼개면 "결제 PAID 인데 주문 미확정" crash gap 이 생기므로 묶는다. (외부 호출 없음 → 트랜잭션 안전, 주문+결제 동일 DB.)
- `pay()` 의 비트랜잭션 규칙과 충돌하지 않는다 — 비트랜잭션이어야 하는 이유는 *흐름 안의 외부 HTTP 호출*(설계 §4.1)인데, confirm 경로는 PG 조회가 `confirmResolved` **이전**에 이미 끝나 있다.
- 콜백은 PG status 문자열을 받으므로 `confirmResult(...)`(문자열 매핑) → `confirmResolved(...)` 로 위임하고, 폴링은 이미 매핑된 `PaymentStatus` 로 `confirmResolved(...)` 를 직접 호출해 같은 확정 경로를 공유한다.

---

## 4. 인터페이스 레이어 (`interfaces/api/payment`)

### 4.1 `PaymentV1Controller`
| 메서드 | 엔드포인트 | 인증 | 비고 |
|---|---|---|---|
| `pay` | `POST /api/v1/payments` | `X-Loopers-LoginId/Pw` | 202 + PENDING 안내 |
| `get` | `GET /api/v1/payments/{id}` | `X-Loopers-LoginId/Pw` | **결과 통지 = 클라이언트 폴링**(설계 §5). 현재 `status`(PENDING/PAID/FAILED/UNKNOWN)·`failureReason` 노출. 소유자 검증 필수 |
| `callback` | `POST /api/v1/payments/callback` | **공개(인증 제외)** | PG 통보 수신 |
| `reconcile` | `POST /api/v1/payments/{id}/reconcile` | 관리자 | 수동 복구 |

- **결과 통지는 서버 푸시 없이 클라이언트 폴링**으로 처리한다(설계 §10-6). `GET`은 DB에 확정된 상태를 그대로 노출할 뿐이고, 상태를 확정하는 주체는 §3.2 콜백/폴링이다. 별도 통지 인프라(WebSocket/SSE/알림)는 두지 않는다.

### 4.2 `PaymentV1Dto` (record)
- `PaymentRequest(orderId, cardType, cardNo)` — **amount 없음**(서버 도출).
- `PaymentResponse(paymentId, orderNumber, status, message)`.
- `PaymentStatusResponse(paymentId, orderNumber, status, failureReason)` — **클라이언트 폴링용 조회 응답**(`GET /payments/{id}`). 확정된 DB 상태를 그대로 노출.
- `CallbackRequest` — **PG가 보내는 페이로드는 `TransactionInfo` 전체**(pg-simulator의 `PaymentCoreRelay`가 `RestTemplate.postForEntity(callbackUrl, transactionInfo, ...)`로 전송): `transactionKey`, `orderId`, `cardType`, `cardNo`, `amount`, `status`, `reason`. **수신 record는 이 필드 전체를 받도록** 정의하고(역직렬화 실패 방지), `status`는 PG enum `PENDING/SUCCESS/FAILED` 문자열 → 우리 PaymentStatus로 매핑(`SUCCESS→PAID`, `FAILED→FAILED`, `PENDING→`보류).
  - **무결성 가드(설계 §6.2)**: 전이 전 콜백의 `amount`·`cardNo`(마스킹 비교)가 우리가 저장한 `PaymentModel` 값과 일치하는지 확인한다. **불일치하면 `PAID`/`FAILED`로 전이하지 않고 `markUnknown(paymentId)`으로 격리 + 운영 알림 로그**를 남긴다(금액 위변조·오배달 방어). 진위 검증을 생략(§4.3)한 만큼 이 대조가 유일한 무결성 방어선이다.

### 4.3 콜백 엔드포인트 공개 (구현 보정)
- **commerce-api 에는 전역 인증 인터셉터가 없다.** 각 컨트롤러 핸들러가 `@RequestHeader(AuthHeaders.LOGIN_ID/LOGIN_PW)` 로 로그인 정보를 직접 받고 Facade 가 `userService.getLoginUser(...)` 로 검증한다. → **별도 화이트리스트 등록이 불필요**하다. 콜백 핸들러(`callback`)는 **로그인 헤더 파라미터를 선언하지 않으므로 자연히 공개**된다.
- **콜백 진위 검증은 생략한다**(설계 §10-3) — 이 과제는 견고한 인증/인가를 다루지 않고 `loginId`/`loginPw` 헤더 수준의 로그인만 처리하므로, 콜백에도 동일 수위를 맞춘다. 서명/IP 화이트리스트는 구현하지 않는다. 대신 **§4.2 amount·cardNo 무결성 가드**가 잘못된 콜백으로 인한 오염을 막는 1차 방어선 역할을 한다.

---

## 5. 스케줄러 (`infrastructure` 또는 별도 `support/scheduler`)

```java
@Scheduled(fixedDelay = 5000) // 폴링 주기
public void reconcilePendingPayments() {
    ZonedDateTime grace = ZonedDateTime.now().minusSeconds(10); // grace period
    for (PaymentModel p : paymentService.findPendingForReconcile(grace)) {
        // transactionKey 있으면 단건 조회, 없으면 orderNumber로 조회
        // - SUCCESS/FAILED  → confirm(...) (조건부 UPDATE, affected=1일 때만 후처리)
        // - 처리 중(PENDING) → 건드리지 않고 다음 주기 재확인
        // - 주문 없음(미도달) → transitionToFailed(...) 로 FAILED 확정 (자동 재요청 X, 사용자 재시도)
        // - createdAt 10분 초과   → markUnknown(...) 격리
    }
}
```
- `@EnableScheduling` 활성화 필요(설정 클래스).
- 조회는 `@CircuitBreaker(name="paymentQuery")` 경유.
- **"주문 없음(미도달)" 확정 시 시스템은 자동 재요청하지 않는다**(설계 §10-5). 돈이 안 빠진 게 증명되므로 재시도해도 안전하지만, 임의 재호출 대신 **FAILED로 떨구고 사용자 재시도에 맡긴다.**

---

## 6. 설정 (`application.yml` — 모듈 yml 규약 따라 프로필별 `---` 블록)

> 설계 §7 기준값. 타임아웃 값(1000/2000)은 학습 노트 근거로 산정. PG 포트(8082)는 **pg-simulator 자체 `application.yml`(`server.port: 8082`)** 에서 확인한 값이다(외부 연동 대상).

```yaml
payment-gateway:
  url: http://localhost:8082            # pg-simulator (pg-simulator/application.yml: server.port=8082)
  callback-url: http://localhost:8080/api/v1/payments/callback
  user-id: loopers                      # PG는 X-USER-ID 헤더만 요구

# Feign 타임아웃 (Connect/Read) — 자동 재시도는 끄고 resilience4j Retry로 일원화
feign:
  client:
    config:
      pgSimulator:
        connectTimeout: 1000
        readTimeout: 2000

resilience4j:
  circuitbreaker:
    instances:
      paymentRequest:                    # POST (보호 핵심 경로)
        sliding-window-type: TIME_BASED
        sliding-window-size: 30
        minimum-number-of-calls: 20
        failure-rate-threshold: 60
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 5
        automatic-transition-from-open-to-half-open-enabled: true
        record-exceptions:
          # 상위 타입 하나로 미도달(5xx)과 Read Timeout(하위 타입) 모두 실패 집계
          - com.loopers.domain.payment.PaymentGatewayException
        ignore-exceptions:
          - com.loopers.support.error.CoreException              # 4xx 입력 검증 등
      paymentQuery:                      # GET (폴링/조회 — 분리)
        sliding-window-type: TIME_BASED
        sliding-window-size: 30
        minimum-number-of-calls: 10
        failure-rate-threshold: 60
        wait-duration-in-open-state: 10s
  retry:
    instances:
      paymentRequest:
        max-attempts: 3
        wait-duration: 200ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        enable-randomized-wait: true     # Jitter (Thundering Herd 방지)
        randomized-wait-factor: 0.5
        retry-exceptions:
          # 미도달(5xx·Connect Timeout)만 자동 재시도 — "돈 안 빠짐"이 증명되어 이중결제 없음
          - com.loopers.domain.payment.PaymentGatewayException
        ignore-exceptions:
          - com.loopers.support.error.CoreException                  # 4xx 영구 실패
          # Read Timeout은 "응답만 유실" 가능 → 블라인드 재시도 금지(이중결제 위험).
          # 하위 타입을 ignore해 부모(PaymentGatewayException) 매칭에서 제외. PENDING 유지 → 폴링 확정(§5)
          - com.loopers.domain.payment.PaymentGatewayTimeoutException
```

> [!important] retry-exceptions에 타임아웃을 넣지 않는다
> resilience4j `@Retry`는 시도 사이에 "주문 없음" 조회를 끼울 수 없으므로, Read Timeout을 재시도 대상에 넣으면 학습 노트가 경고한 **"타임아웃 후 블라인드 재시도 = 이중결제"** 함정에 빠진다. 타임아웃 건은 **PENDING으로 남겨 폴링/조회(§5)가 미도달을 확인한 뒤에만** 재요청/FAILED 처리한다. (`ignore-exceptions`는 `retry-exceptions`보다 우선하므로 하위 타입 제외가 확실히 동작한다.)

> `ErrorType`에 결제 관련 카테고리가 필요하면 enum에 추가(예: `PAYMENT_GATEWAY_ERROR`). 컨트롤러에서 직접 `ResponseEntity` 만들지 않는다.

---

## 7. 주문 도메인 변경 (결합 지점)

- `OrderStatus`에 결제 결과 반영 상태 추가: 현재 `PLACED`만 → `PAID`, `PAYMENT_FAILED` 추가(또는 별도 결제상태 필드 분리 — 결정 항목).
- `OrderService`: `getByOrderNumberAndValidateOwner(orderNumber, userId)`, `markPaid(orderId)`, `markPaymentFailed(orderId)` 추가.

---

## 8. 테스트 전략 (CLAUDE.md 레이어별 전략 준수)

| 레이어 | 종류 | 핵심 케이스 |
|---|---|---|
| `PaymentModel` | 단위 | 상태 전이 규칙(terminal 불변), 카드 마스킹, amount 검증 |
| `PaymentService` | 단위(Repository mock) | `confirm` 시 affected=1/0에 따른 반환, grace 조회 |
| `PaymentGatewayImpl` | 통합/단위 | CB OPEN 시 Fallback, Retry 동작, PG status 매핑 |
| `PaymentFacade` | **통합(Testcontainers)** | Tx 분리 검증: PG 예외 시 Payment가 **PENDING으로 남아있는지** DB 재조회 / 정상 시 transactionKey 저장 |
| 콜백·폴링 동시성 | 통합 | 콜백+폴링 동시 confirm 시 **후처리 1회**만 실행되는지(조건부 UPDATE) — real 제약 배경 `assertDoesNotThrow` + 상태 단언 |
| 무결성 가드 | 통합 | 콜백 `amount`/`cardNo` 불일치 시 **전이 거부 + UNKNOWN 격리**(PAID로 안 넘어감) DB 상태 단언 |
| 재결제 | 통합 | FAILED 주문 재결제 시 **새 row 생성** / 활성(PENDING/PAID) 결제 존재 시 PG 재호출 없이 **멱등 반환** |
| Controller | E2E(RANDOM_PORT) | 성공: 202 + PENDING 응답 **및** DB 영속 상태, `GET /payments/{id}`로 상태 폴링 / 실패: 상태 코드 |

- `verify()`/`any()` 지양. 동시성 강검증은 통합 테스트로.
- PG 호출은 통합 테스트에서 실제 pg-simulator 대신 **stub/WireMock 또는 `PaymentGateway` Fake**로 시나리오(미도달/타임아웃/CB OPEN) 재현.

---

## 9. 구현 순서 (PR 단위)

1. **도메인 골격**: `CardType`, `PaymentStatus`, `PaymentModel`, `PaymentRepository`, `PaymentService`(+단위 테스트).
2. **PG 포트/어댑터**: `PaymentGateway`, FeignClient, `PaymentGatewayImpl` + Timeout 설정.
3. **결제 요청 흐름**: `PaymentFacade`(Tx 분리), Controller `POST /payments` + `GET /payments/{id}`(클라이언트 폴링용 조회), 주문 결합 + E2E.
4. **CircuitBreaker + Fallback** 적용 및 설정(Must-Have 완성).
5. **콜백 수신** + 조건부 UPDATE 후처리 + 동시성 통합 테스트.
6. **폴링 스케줄러** + 수동 복구 API + UNKNOWN 격리.
7. **Retry**(Nice-To-Have) + Jitter.

---

## 10. Quest Checklist 매핑 (누락 검증)

### PG 연동 대응
| 체크리스트 | 충족 위치 |
|---|---|
| RestTemplate/FeignClient로 호출 | §2.3 `PaymentGatewayFeignClient` |
| 타임아웃 설정 + 실패 시 예외 처리 | §6 Feign Connect/Read, §2.4 예외 변환 |
| 결제 요청 실패 응답 시스템 연동 | §3.1 미도달 시 PENDING 유지, §5 폴링 복구 |
| 콜백 + 상태 확인 API 연동 | §1.6/§4 콜백, §5 폴링(`getByKey`/`getByOrderId`) |

### Resilience 설계
| 체크리스트 | 충족 위치 |
|---|---|
| 서킷브레이커/재시도로 장애 확산 방지 | §6 `paymentRequest` CB + Retry |
| 외부 장애 시 내부 정상 응답 보호 | §3.1 Fallback → PENDING "처리 중" (트랜잭션 밖 호출 §4) |
| 콜백 없이도 주기/수동 API로 복구 | §5 폴링 스케줄러 + §4.1 수동 reconcile |
| 타임아웃 실패해도 결제 건 확인 후 반영 | §5 `transactionKey`/`orderNumber` 되짚기 조회 후 confirm |

### Must-Have / Nice-To-Have
| 항목 | 충족 위치 |
|---|---|
| Fallback | §3.1 facade try-catch → PENDING "처리 중"(어댑터는 예외 전파, fallbackMethod 미사용) / 설계 §7.3 |
| Timeout | §6 / 설계 §7.1 |
| CircuitBreaker | §6 / 설계 §7.2 |
| Retryer (NTH) | §6 retry / 설계 §7.4 |

---

## 관련 문서
- [[payment-design]] — 설계 원본
- [[Round 6 - 학습 정리 (Resilience 심화)]]
- [[round6-quests]]
