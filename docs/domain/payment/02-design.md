# Payment 도메인 설계

- 작성일: 2026-06-24
- 범위: Commerce-API Payment 도메인 신규 추가

---

## 1. 설계 결정 요약

| 항목 | 결정 |
|------|------|
| 비동기 처리 방식 | Callback 수신 (주) + timeout 시 1차 Polling (보조) |
| 클라이언트 응답 | 콜백 수신 시 즉시 반환. timeout 시 PG 1회 직접 조회 후 반환 |
| 비동기 응답 홀더 | `CompletableFuture` (Java 8+ 표준) |
| 대기 상태 매핑 | `PaymentWaitingRegistry` (ConcurrentHashMap, 인메모리) |
| PG HTTP 클라이언트 | RestTemplate + Resilience4j 서킷브레이커 |
| 재배포 유실 대응 | Graceful Shutdown + Client Polling fallback |
| PENDING 복구 | Batch Scheduler (설계만 포함, 현재 과제 구현 범위 제외) |
| 환불 발동 조건 | PG SUCCESS 확인 후 내부 처리 실패 시에만 발동 |

---

## 2. 도메인 모델

### PaymentEntity

```
fields:
  - id: Long                          // PK
  - orderId: Long                     // 주문 ID
  - userId: Long                      // 유저 ID
  - transactionKey: String (nullable) // PG 발급 키. 요청 실패 시 null
  - cardType: CardType                // SAMSUNG / KB / HYUNDAI
  - cardNo: String                    // 카드 번호
  - amount: Long                      // 결제 금액 (주문 finalAmount)
  - status: PaymentStatus             // PENDING / SUCCESS / FAILED
  - failureReason: String (nullable)  // 실패 사유

business methods:
  - approve(): void                   // PENDING → SUCCESS
  - fail(String reason): void         // PENDING → FAILED
  - isOwnedBy(Long userId): boolean
```

### PaymentStatus 전이

```
PENDING ──[콜백: SUCCESS]──────────────────────────────► SUCCESS  →  OrderStatus = PAID
        ──[콜백: FAILED]───────────────────────────────► FAILED
        ──[PG 요청 실패 / 서킷 OPEN]──────────────────► FAILED   (즉시, CompletableFuture 미생성)
        ──[timeout → 1차 Poll: SUCCESS]────────────────► SUCCESS  →  OrderStatus = PAID
        ──[timeout → 1차 Poll: FAILED]─────────────────► FAILED
        ──[timeout → 1차 Poll: PENDING or 오류]────────► PENDING  (Scheduler가 이후 복구)
```

**approve() / fail() 멱등성 보장:**
콜백과 timeout 1차 Poll이 동시에 완료되는 경우 DB 업데이트가 중복 시도될 수 있다.
`approve()` / `fail()`은 이미 SUCCESS / FAILED 상태이면 무시하는 가드를 포함한다.

### CardType

```java
enum CardType { SHINHAN, SAMSUNG, KB, HYUNDAI, LOTTE, WOORI, HANA, BC }
```

| 값 | 카드사 |
|----|--------|
| `SHINHAN` | 신한카드 |
| `SAMSUNG` | 삼성카드 |
| `KB` | KB국민카드 |
| `HYUNDAI` | 현대카드 |
| `LOTTE` | 롯데카드 |
| `WOORI` | 우리카드 |
| `HANA` | 하나카드 |
| `BC` | 비씨카드 |

### 내부 상태 vs 외부 상태 불일치 지점

| 상황 | 내부(PaymentEntity) | 외부(PG) | 불일치 |
|------|---------------------|----------|--------|
| 정상 흐름 | SUCCESS | SUCCESS | ✅ |
| PG 요청 응답 유실 | FAILED | PENDING or SUCCESS | ⚠️ |
| TX3 실패 (콜백 후 DB 롤백) | PENDING | SUCCESS or FAILED | ⚠️ |
| 콜백 미수신 / CompletableFuture timeout | PENDING | SUCCESS or FAILED | ⚠️ |

---

## 3. API 명세

| Method | URL | 설명 | 인증 |
|--------|-----|------|------|
| POST | `/api/v1/payments` | 결제 요청. 최대 10초 대기 후 결과 반환 | User |
| POST | `/api/v1/payments/callback` | PG 콜백 수신 (내부 엔드포인트) | 없음 |
| GET  | `/api/v1/payments/{paymentId}` | 결제 상태 조회 (Client Polling fallback) | User |

### POST /api/v1/payments

```http
POST {{commerce-api}}/api/v1/payments
X-Loopers-LoginId:
X-Loopers-LoginPw:
Content-Type: application/json

{
  "orderId": "1351039135",
  "cardType": "SAMSUNG",
  "cardNo": "1234-5678-9814-1451"
}
```

**Response**

```json
// 성공 (10초 내 콜백 수신)
{ "data": { "paymentId": 1, "transactionKey": "20250816:TR:9577c5", "status": "SUCCESS" } }

// 실패
{ "data": { "paymentId": 1, "transactionKey": "20250816:TR:9577c5", "status": "FAILED", "reason": "한도 초과" } }

// 타임아웃 (10초 초과 + 1차 Poll도 PENDING)
{ "data": { "paymentId": 1, "transactionKey": "20250816:TR:9577c5", "status": "PENDING" } }

// PG 요청 자체 실패
{ "error": { "code": "PAYMENT_GATEWAY_ERROR", "message": "결제 요청에 실패했습니다." } }
```

> **Client Polling 안내**: 연결 오류 수신 시 `GET /api/v1/payments/{paymentId}`로 상태를 재조회한다.

### GET /api/v1/payments/{paymentId} — 결제 상태 조회 (Client Polling)

POST /api/v1/payments 응답으로 연결 오류 또는 PENDING을 수신한 경우, 클라이언트가 주기적으로 호출하여 최종 결제 상태를 확인한다.

```http
GET {{commerce-api}}/api/v1/payments/{paymentId}
X-Loopers-LoginId:
X-Loopers-LoginPw:
```

**Response**

```json
// 결제 완료 (SUCCESS)
{ "data": { "paymentId": 1, "transactionKey": "20250816:TR:9577c5", "status": "SUCCESS" } }

// 결제 실패 (FAILED)
{ "data": { "paymentId": 1, "transactionKey": "20250816:TR:9577c5", "status": "FAILED", "reason": "한도 초과" } }

// 아직 처리 중 (PENDING)
{ "data": { "paymentId": 1, "transactionKey": "20250816:TR:9577c5", "status": "PENDING" } }
```

**Client Polling 시나리오**

```
POST /payments 응답
  ├─ 연결 오류 (504 / Connection Reset)  → GET /payments/{id} 재조회
  └─ 200 OK { status: PENDING }          → GET /payments/{id} 재조회

GET /payments/{id}
  ├─ DB status = SUCCESS / FAILED  → 즉시 반환
  └─ DB status = PENDING
        → PG 직접 조회 (PgClient.getTransaction)
        ├─ PG = SUCCESS  → DB 업데이트 → SUCCESS 반환
        ├─ PG = FAILED   → DB 업데이트 → FAILED 반환
        ├─ PG = PENDING  → PENDING 반환 → N초 후 재조회
        └─ PG 응답 없음  → 500 PG_QUERY_ERROR (에러 처리)
```

---

## 5. 레이어 구조

```
interfaces/api/payment
  PaymentV1Controller         POST /api/v1/payments
                              POST /api/v1/payments/callback
                              GET  /api/v1/payments/{paymentId}
  PaymentV1Dto                요청/응답 DTO (record)

application/payment
  PaymentApplicationService   initiate(), processCallback(), getPayment()
                              ※ getPayment(): DB status = PENDING 시 PgClient 직접 조회
  PaymentInfo                 Facade 반환 DTO

domain/payment
  PaymentEntity               도메인 모델 (상태 전이 메서드 포함)
  PaymentRepository           포트 인터페이스
  PaymentStatus               PENDING / SUCCESS / FAILED
  CardType                    SAMSUNG / KB / HYUNDAI
  PgClient                    PG 호출 포트 인터페이스

infrastructure/payment
  PaymentJpaRepository        Spring Data JPA
  PaymentRepositoryImpl       PaymentRepository 구현체
  PgRestClient                PgClient 구현체 (RestTemplate + 서킷브레이커)

support/payment
  PaymentWaitingRegistry      transactionKey → CompletableFuture 인메모리 매핑

batch (미구현 — 추후 commerce-batch 모듈)
  PaymentRecoveryScheduler    PENDING 결제건 주기적 PG 조회 및 상태 동기화
```

---

## 6. 트랜잭션 경계

```
[TX1] Order 검증 + 중복 결제 검증 + PaymentEntity(PENDING) 저장
       ↓ 커밋

[TX 외부] PgClient.requestPayment() — 서킷브레이커 + timeout 1s
       ├─ 실패 → [TX2] PaymentEntity = FAILED → 즉시 에러 응답 반환
       └─ 성공 → [TX2] transactionKey 저장
                  CompletableFuture 생성 (timeout=10s) & Registry 등록
                  return responseFuture

       ↓ (1~5초 후 PG 처리 완료)

[TX3] PaymentEntity 상태 업데이트 + (SUCCESS 시) OrderEntity = PAID
      → 두 업데이트를 하나의 트랜잭션으로 묶음
      → PaymentEntity UPDATE 성공 후 OrderEntity UPDATE 실패 시 둘 다 롤백
      → PENDING 상태로 복원 → Scheduler가 이후 PG 재조회
```

**TX3를 하나의 트랜잭션으로 묶는 이유:**  
PaymentEntity = SUCCESS, OrderEntity = PENDING 불일치를 방지한다.  
롤백되어 PENDING으로 복원되면 Scheduler가 PG를 재조회해 자동 복구한다.

**TX1 커밋 이후 PG 응답 유실 시:**  
transactionKey가 null이므로 `/sync`로 직접 조회 불가.  
`GET /pg/payments?orderId=...` API를 통해 orderId 기반 복구 경로를 사용한다.

---

## 7. 핵심 컴포넌트

### PG 연동 공통 헤더 (PgRestClient)

PG 게이트웨이로 나가는 **모든 아웃바운드 호출**(`requestPayment`, `getTransaction`)은
요청 주체를 식별하기 위해 `X-USER-ID` 헤더를 **필수**로 포함한다.

```
헤더: X-USER-ID: {userId}     // 결제를 요청한 유저 ID. 모든 PG 호출에 필수
전달: PgClient 포트 메서드 시그니처에 userId 파라미터 포함
      - requestPayment(PgPaymentRequest request, Long userId)
      - getTransaction(String transactionKey, Long userId)
구현: PgRestClient 에서 HttpHeaders 에 X-USER-ID 를 설정해 exchange 로 전송
가드: userId == null 이면 BAD_REQUEST (PG 호출 전 차단)
```

> userId 는 body(JSON)가 아닌 **전송(헤더) 관심사**로 분리한다.
> `getTransaction` 은 request body 가 없으므로 헤더 전달을 위해서도 메서드 파라미터가 필요하다.

### PaymentWaitingRegistry

```
역할: transactionKey → CompletableFuture 인메모리 매핑 보관
구현: ConcurrentHashMap (멀티스레드 안전)
특성: pop() = get + remove 원자적 실행
      → 콜백 중복 수신 시 두 번째는 Optional.empty() 반환
      → isDone() 으로 이미 완료된 future 재완료 시도 방지
주의: 서버 재시작 시 Registry 소멸
      → PENDING 결제건 복구는 Scheduler 담당
```

### 결제 요청 흐름 (PaymentApplicationService.initiate)

```
1. [TX1] Order 검증 + 중복 결제 검증 + PaymentEntity(PENDING) 저장
2. [TX 외부] PgClient.requestPayment()
   - 실패 → [TX2] FAILED 처리 → CoreException 발생
   - 성공 → [TX2] transactionKey 저장

3. CompletableFuture 구성
   innerFuture = new CompletableFuture<>()     // 콜백이 직접 완료시키는 future
   Registry.register(transactionKey, innerFuture)

   responseFuture = innerFuture
       .orTimeout(10, TimeUnit.SECONDS)        // 10초 초과 시 TimeoutException 발생
       .exceptionally(ex → {
           if (ex is TimeoutException) {
               Registry.pop(transactionKey)    // Registry 정리
               result = PgClient.getTransaction(transactionKey)  // 1차 Poll (timeout: 15s)
               if result = SUCCESS → [TX] DB 업데이트 → return SUCCESS 응답
               if result = FAILED  → [TX] DB 업데이트 → return FAILED 응답
               if result = PENDING or 오류    → return PENDING 응답
           }
           return errorResponse()
       })

4. return responseFuture   // Spring MVC가 비동기 응답으로 처리
```

**innerFuture / responseFuture 분리 이유:**
- `innerFuture`: Registry에 등록. 콜백이 `complete(result)`로 완료
- `responseFuture`: Spring MVC에 반환. timeout + 1차 Poll 처리 담당
- 콜백이 `innerFuture`를 완료시키면 `orTimeout()`이 취소되어 `exceptionally()`는 실행되지 않음

### 콜백 수신 흐름 (PaymentApplicationService.processCallback)

```
1. [TX3] PaymentEntity 상태 업데이트 (DB 커밋 선행)
         SUCCESS → OrderEntity.status = PAID (같은 TX)
2. Registry.pop(transactionKey)
   - 존재 + isDone() = false: future.complete(결과) → 클라이언트 응답 전송
   - 존재 + isDone() = true : orTimeout이 먼저 완료 → 무시
   - 없음: timeout 이미 발생하여 1차 Poll이 처리 중
           → DB 업데이트만으로 충분 (approve() 가드로 중복 처리 방지)
3. return 200 OK

※ DB 커밋 선행 이유:
   클라이언트가 응답 수신 직후 주문 조회 시 DB가 아직 PENDING인 상황을 방지
```

---

## 8. Resilience 설계

### 타임아웃

| 구간 | 값 | 근거 |
|------|----|------|
| PG connectTimeout | 500ms | |
| PG readTimeout | 1s | 요청 지연 최대 500ms + 여유 |
| CompletableFuture timeout | 10s | 처리 지연 최대 5s + 5s 여유. 콜백 응답 대기 |
| 1차 Poll readTimeout | 15s | CompletableFuture timeout 이후 PG 최종 확인 대기 |

> 클라이언트 최대 대기 시간: CompletableFuture(10s) + 1차 Poll(최대 15s) = **최대 25s**  
> 단, 1차 Poll은 PG가 이미 처리 완료한 상태를 조회하므로 실제로는 훨씬 빠르게 반환된다.

### 서킷브레이커 (Resilience4j)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      pgClient:
        slidingWindowSize: 10
        failureRateThreshold: 50
        slowCallDurationThreshold: 2s    # PG readTimeout(1s) 기준 slow call 판정
        slowCallRateThreshold: 50        # slow call 50% 이상 시 OPEN
        waitDurationInOpenState: 15s     # 1차 Poll timeout 고려해 기존 10s → 15s
        permittedNumberOfCallsInHalfOpenState: 3
  timelimiter:
    instances:
      pgClient:
        timeoutDuration: 2s              # 서킷브레이커 TimeLimiter: PG 호출 전체 제한
```

- 서킷 OPEN 시: PG 호출 없이 즉시 `PAYMENT_GATEWAY_ERROR` 반환
- CompletableFuture를 생성하지 않음 (대기 없이 즉시 응답)
- `slowCallDurationThreshold`: 1s 초과 호출을 slow call로 집계하여 서킷 감도 향상

---

## 9. 복구 전략

### PENDING 결제건 처리 원칙

"모른다"를 즉시 "실패"로 단정하지 않는다.  
PG가 카드를 승인했을 가능성이 있으므로, 실제 처리 결과를 확인한 후 상태를 결정한다.

### 계층별 방어 전략

```
Layer 1: CompletableFuture + Callback + 1차 Poll
         → 정상 케이스 및 콜백 지연/유실 커버
         → 구현 대상 ✅

Layer 2: Graceful Shutdown
         → 계획된 재배포 시 in-flight 요청 완료 대기
         → 설정값 추가로 적용 ✅
         server.shutdown=graceful
         spring.lifecycle.timeout-per-shutdown-phase=30s

Layer 3: Client Polling (연결 끊김 fallback)
         → Graceful Shutdown 초과 / 예기치 않은 서버 종료 시
         → 클라이언트가 연결 오류 감지 후 GET /payments/{paymentId} 재조회
         → API 계약에 명시 ✅

Layer 4: Batch Scheduler
         → 서버 OOM / 강제 종료 등 극단적 케이스의 PENDING 잔류 건 복구
         → 설계 범위에 포함하나 현재 과제 구현 범위에서 제외
         → 추후 commerce-batch 모듈에 별도 구현 예정 📋
```

### Layer 4 Batch Scheduler 설계 (미구현)

```
30초 간격 실행
    ↓
PENDING 결제건 조회
    ↓
PG 상태 조회
  - transactionKey 존재: GET /pg/payments/{transactionKey}
  - transactionKey 없음: GET /pg/payments?orderId={orderId}
    ↓
PG 응답 기준으로 상태 확정
  PG = SUCCESS → PaymentEntity = SUCCESS + OrderEntity = PAID
  PG = FAILED  → PaymentEntity = FAILED
  PG = PENDING → 재시도 카운트 증가
    ↓
30분 이상 PENDING 지속 → FAILED 처리 + 슬랙 알림
```

### 환불 발동 조건

| 상황 | 환불 필요 여부 |
|------|--------------|
| PG 요청 자체 실패 (요청 미수신) | ❌ 카드 미승인 |
| PG FAILED (한도 초과 / 잘못된 카드) | ❌ 카드 미승인 |
| PG SUCCESS → 내부 처리 실패 | ✅ 환불 필요 |
| 30분 경과 후 PG 조회 불가 | 📋 CS 수동 처리 |

---

## 10. 장애 시나리오

| 시나리오 | 내부 상태 | 외부 상태 | 복구 방법 |
|----------|-----------|----------|----------|
| PG 요청 타임아웃 (transactionKey 없음) | FAILED | 미수신 | 재결제 시도 |
| PG 응답 수신 후 서버 재시작 | PENDING (transactionKey 없음) | PENDING or SUCCESS | orderId 기반 Scheduler 복구 |
| 콜백 미수신 / CompletableFuture timeout | PENDING | SUCCESS or FAILED | **1차 Poll** → 결과 반환. Poll도 실패 시 Scheduler |
| 콜백 지연 + timeout + 1차 Poll 동시 | approve() 가드로 중복 방지 | SUCCESS or FAILED | 멱등 처리 |
| TX3 실패 (콜백 후 DB 롤백) | PENDING | SUCCESS or FAILED | Scheduler → PG 조회 |
| 콜백 중복 수신 | 첫 번째에 의해 확정 | SUCCESS or FAILED | Registry.pop() 멱등 처리 |
| 서킷 OPEN 중 요청 | FAILED (즉시) | 호출 안 됨 | 서킷 HALF_OPEN 후 재결제 |
| PG SUCCESS → OrderEntity 업데이트 실패 | PENDING (TX3 롤백) | SUCCESS | Scheduler → PG 조회 |
| Client Polling 중 PG 조회 오류 | PENDING 유지 | 알 수 없음 | 500 PG_QUERY_ERROR 반환 → 클라이언트 재시도 판단 |
| 30분 경과 후 PG 조회 불가 | FAILED | 알 수 없음 | CS 수동 처리 |

---

## 11. 관련 ADR

추후 결정 사항은 `docs/adr/` 에 별도 기록한다.
