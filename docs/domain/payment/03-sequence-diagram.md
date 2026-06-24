# Payment 도메인 시퀀스 다이어그램

- 작성일: 2026-06-24
- 최종 수정: 2026-06-25

---

## 1. POST /api/v1/payments — 정상 흐름 (콜백 10초 내 수신)

```mermaid
sequenceDiagram
    participant C as Client
    participant CTL as PaymentV1Controller
    participant SVC as PaymentApplicationService
    participant REG as PaymentWaitingRegistry
    participant PAY as PaymentEntity
    participant PGC as PgClient
    participant PG as PG Simulator
    participant PR as PaymentRepository
    participant OR as OrderRepository

    C->>CTL: POST /api/v1/payments {orderId, cardType, cardNo}
    CTL->>SVC: initiate(userId, request)

    Note over SVC,OR: [TX1] 검증 + 저장
    SVC->>OR: findById(orderId)
    OR-->>SVC: OrderEntity
    SVC->>PAY: validate (isOwnedBy, status=PENDING)
    SVC->>PR: existsByOrderIdAndStatusIn(PENDING, SUCCESS)
    PR-->>SVC: false (중복 없음)
    SVC->>PAY: new PaymentEntity(PENDING)
    SVC->>PR: save(payment)
    PR-->>SVC: PaymentEntity(id, PENDING)
    Note over SVC: TX1 커밋

    Note over SVC,PG: [TX 외부] PG 호출
    SVC->>PGC: requestPayment(request)
    PGC->>PG: POST /pg/api/v1/payments {orderId, cardType, cardNo, amount, callbackUrl}
    PG-->>PGC: 200 OK {transactionKey, status: PENDING}
    PGC-->>SVC: transactionKey

    Note over SVC,PR: [TX2] transactionKey 저장
    SVC->>PR: updateTransactionKey(paymentId, transactionKey)
    Note over SVC: TX2 커밋

    SVC->>REG: register(transactionKey, innerFuture)
    Note over SVC: responseFuture = innerFuture.orTimeout(10s).exceptionally(...)
    SVC-->>CTL: responseFuture
    CTL-->>C: (HTTP 연결 유지, Tomcat 스레드 반환)

    Note over PG: 비동기 처리 (1~5s)

    PG->>CTL: POST /api/v1/payments/callback {transactionKey, status: SUCCESS}
    CTL->>SVC: processCallback(request)

    Note over SVC,OR: [TX3] 상태 업데이트
    SVC->>PR: findByTransactionKey(transactionKey)
    PR-->>SVC: PaymentEntity
    SVC->>PAY: approve()
    SVC->>OR: findById(orderId)
    OR-->>SVC: OrderEntity
    SVC->>OR: save(order.pay())
    Note over SVC: TX3 커밋 (PaymentEntity + OrderEntity 함께)
    SVC-->>CTL: done

    CTL->>REG: pop(transactionKey)
    REG-->>CTL: Optional[innerFuture]
    CTL->>CTL: isDone() = false → innerFuture.complete(SUCCESS 응답)

    CTL-->>C: 200 OK {paymentId, transactionKey, status: SUCCESS}
    CTL-->>PG: 200 OK
```

---

## 2. POST /api/v1/payments — timeout + 1차 Poll (10초 초과)

```mermaid
sequenceDiagram
    participant C as Client
    participant CTL as PaymentV1Controller
    participant SVC as PaymentApplicationService
    participant REG as PaymentWaitingRegistry
    participant PGC as PgClient
    participant PG as PG Simulator
    participant PR as PaymentRepository
    participant OR as OrderRepository

    C->>CTL: POST /api/v1/payments
    CTL->>SVC: initiate(userId, request)

    Note over SVC,PR: [TX1] 저장 + [TX2] transactionKey 저장
    SVC->>REG: register(transactionKey, innerFuture)
    Note over SVC: responseFuture = innerFuture<br/>.orTimeout(10s)<br/>.exceptionally(ex → poll1st())
    SVC-->>CTL: responseFuture
    CTL-->>C: (HTTP 연결 유지)

    Note over PG: 처리 중... (10s 초과)

    Note over CTL: ⏱ orTimeout(10s) 발화<br/>TimeoutException → exceptionally() 실행

    CTL->>REG: pop(transactionKey)
    REG-->>CTL: Optional[innerFuture] (정리)

    Note over CTL: 1차 Poll 시작
    CTL->>PGC: getTransaction(transactionKey)
    PGC->>PG: GET /pg/api/v1/payments/{transactionKey} (timeout: 15s)

    alt PG = SUCCESS
        PG-->>PGC: {status: SUCCESS}
        PGC-->>CTL: SUCCESS

        Note over CTL,OR: [TX] 상태 업데이트
        CTL->>SVC: processCallback(transactionKey, SUCCESS)
        SVC->>PR: findByTransactionKey → PaymentEntity.approve()
        SVC->>OR: OrderEntity.pay()
        Note over SVC: TX 커밋
        SVC-->>CTL: done

        CTL-->>C: 200 OK {status: SUCCESS}

    else PG = FAILED
        PG-->>PGC: {status: FAILED, reason}
        PGC-->>CTL: FAILED

        Note over CTL,PR: [TX] 상태 업데이트
        CTL->>SVC: processCallback(transactionKey, FAILED)
        SVC->>PR: PaymentEntity.fail(reason)
        Note over SVC: TX 커밋
        SVC-->>CTL: done

        CTL-->>C: 200 OK {status: FAILED, reason}

    else PG = PENDING or 오류
        PG-->>PGC: PENDING or 오류
        PGC-->>CTL: PENDING

        CTL-->>C: 200 OK {status: PENDING}
        Note over C: Client Polling 진행<br/>GET /api/v1/payments/{paymentId}
    end
```

---

## 3. POST /api/v1/payments/callback — PG 콜백 수신

```mermaid
sequenceDiagram
    participant PG as PG Simulator
    participant CTL as PaymentV1Controller
    participant SVC as PaymentApplicationService
    participant PAY as PaymentEntity
    participant PR as PaymentRepository
    participant OR as OrderRepository
    participant REG as PaymentWaitingRegistry
    participant CF as CompletableFuture (innerFuture)

    PG->>CTL: POST /api/v1/payments/callback {transactionKey, status, reason}

    CTL->>SVC: processCallback(request)

    Note over SVC,OR: [TX3] DB 커밋 선행
    SVC->>PR: findByTransactionKey(transactionKey)
    PR-->>SVC: PaymentEntity

    alt status = SUCCESS
        SVC->>PAY: approve()
        Note over PAY: 이미 SUCCESS면 가드 → 무시 (멱등)
        SVC->>OR: findById(orderId)
        OR-->>SVC: OrderEntity
        SVC->>OR: save(order.pay())
    else status = FAILED
        SVC->>PAY: fail(reason)
        Note over PAY: 이미 FAILED면 가드 → 무시 (멱등)
    end

    SVC->>PR: save(payment)
    Note over SVC: TX3 커밋
    SVC-->>CTL: done

    CTL->>REG: pop(transactionKey)
    REG-->>CTL: Optional[innerFuture]

    alt innerFuture 존재 + isDone() = false
        CTL->>CF: complete(결과)
        Note over CF: orTimeout() 취소<br/>responseFuture 완료 → 클라이언트 응답 전송
    else innerFuture 없음 (timeout 이미 발생)
        Note over CTL: 1차 Poll이 이미 처리 중<br/>DB 업데이트만으로 충분
    else innerFuture 존재 + isDone() = true
        Note over CTL: orTimeout()이 먼저 완료됨<br/>무시
    end

    CTL-->>PG: 200 OK
```

---

## 4. GET /api/v1/payments/{paymentId} — Client Polling (연결 끊김 fallback)

POST /api/v1/payments 응답으로 연결 오류 또는 PENDING을 수신한 경우 클라이언트가 호출한다.  
DB status가 PENDING인 경우 PG를 직접 조회하여 최신 상태를 반환한다.  
PG 조회는 PgClient를 경유하므로 서킷브레이커가 동일하게 적용된다.

> **클라이언트 반복 정책**: 서버는 요청 1건을 독립적으로 처리한다.  
> 클라이언트가 PENDING 응답을 받으면 N초 후 재호출하며, SUCCESS / FAILED / 에러 수신 시 중단한다.

```mermaid
sequenceDiagram
    participant C as Client
    participant CTL as PaymentV1Controller
    participant SVC as PaymentApplicationService
    participant PR as PaymentRepository
    participant PGC as PgClient
    participant CB as CircuitBreaker
    participant PG as PG Simulator

    Note over C: POST /payments 응답으로<br/>연결 오류 or PENDING 수신<br/>→ GET /payments/{id} 호출 (클라이언트가 반복)

    C->>CTL: GET /api/v1/payments/{paymentId}
    CTL->>SVC: getPayment(userId, paymentId)
    SVC->>PR: findById(paymentId)
    PR-->>SVC: PaymentEntity

    alt 존재하지 않음 or 소유자 불일치
        SVC-->>CTL: CoreException(NOT_FOUND)
        CTL-->>C: 404 Not Found

    else DB status = SUCCESS or FAILED
        SVC-->>CTL: PaymentInfo(status)
        CTL-->>C: 200 OK {status}
        Note over C: 폴링 종료

    else DB status = PENDING
        Note over SVC: PG 직접 조회 (PgClient → CircuitBreaker 경유)
        SVC->>PGC: getTransaction(transactionKey)
        PGC->>CB: 서킷브레이커 통과 여부 확인

        alt 서킷 OPEN
            CB-->>PGC: CallNotPermittedException
            PGC-->>SVC: 예외
            SVC-->>CTL: CoreException(PG_QUERY_ERROR)
            CTL-->>C: 500 PG_QUERY_ERROR
            Note over C: 폴링 중단 (에러 처리)

        else 서킷 CLOSED
            CB->>PG: GET /pg/api/v1/payments/{transactionKey}

            alt PG = SUCCESS
                PG-->>CB: {status: SUCCESS}
                CB-->>PGC: SUCCESS
                PGC-->>SVC: SUCCESS
                Note over SVC: [TX] PaymentEntity.approve()<br/>OrderEntity.pay() → 커밋
                SVC-->>CTL: PaymentInfo(SUCCESS)
                CTL-->>C: 200 OK {status: SUCCESS}
                Note over C: 결제 완료 → 폴링 종료

            else PG = FAILED
                PG-->>CB: {status: FAILED, reason}
                CB-->>PGC: FAILED
                PGC-->>SVC: FAILED
                Note over SVC: [TX] PaymentEntity.fail(reason) → 커밋
                SVC-->>CTL: PaymentInfo(FAILED, reason)
                CTL-->>C: 200 OK {status: FAILED, reason}
                Note over C: 결제 실패 → 폴링 종료

            else PG = PENDING
                PG-->>CB: {status: PENDING}
                CB-->>PGC: PENDING
                PGC-->>SVC: PENDING
                SVC-->>CTL: PaymentInfo(PENDING)
                CTL-->>C: 200 OK {status: PENDING}
                Note over C: N초 후 재호출

            else PG 응답 없음 (타임아웃)
                PG--xCB: 타임아웃
                CB-->>PGC: RestClientException
                PGC-->>SVC: 예외
                SVC-->>CTL: CoreException(PG_QUERY_ERROR)
                CTL-->>C: 500 PG_QUERY_ERROR
                Note over C: 폴링 중단 (에러 처리)
            end
        end
    end
```

---

## 5. POST /api/v1/payments — PG 요청 실패 (서킷브레이커)


```mermaid
sequenceDiagram
    participant C as Client
    participant CTL as PaymentV1Controller
    participant SVC as PaymentApplicationService
    participant PAY as PaymentEntity
    participant PR as PaymentRepository
    participant PGC as PgClient
    participant CB as CircuitBreaker
    participant PG as PG Simulator

    C->>CTL: POST /api/v1/payments
    CTL->>SVC: initiate(userId, request)

    Note over SVC,PR: [TX1] PaymentEntity(PENDING) 저장
    SVC->>PGC: requestPayment(request)
    PGC->>CB: 서킷브레이커 통과 여부 확인

    alt 서킷 CLOSED — PG 호출 시도
        CB->>PG: POST /pg/api/v1/payments
        PG--xCB: 타임아웃(1s) or 오류
        CB-->>PGC: RestClientException
        PGC-->>SVC: 예외 발생
        Note over CB: 실패 횟수 누적<br/>10건 중 5건 실패 → OPEN 전환
    else 서킷 OPEN — 즉시 차단
        CB-->>PGC: CallNotPermittedException
        PGC-->>SVC: 예외 발생
        Note over CB: PG 호출 없이 즉시 반환<br/>15s 후 HALF_OPEN 전환
    end

    Note over SVC,PR: [TX2] PaymentEntity = FAILED
    SVC->>PAY: fail("PG 요청 실패")
    SVC->>PR: save(payment)
    Note over SVC: TX2 커밋
    Note over SVC: CompletableFuture 미생성

    SVC-->>CTL: CoreException(PAYMENT_GATEWAY_ERROR)
    CTL-->>C: 502 PAYMENT_GATEWAY_ERROR
```

---

## 6. 재배포 유실 + Client Polling fallback (Layer 2 + Layer 3)

```mermaid
sequenceDiagram
    participant C as Client
    participant A as Commerce-API (구 인스턴스)
    participant B as Commerce-API (신 인스턴스)
    participant REG_A as Registry (구 인스턴스, in-memory)
    participant PG as PG Simulator
    participant DB as Database

    C->>A: POST /api/v1/payments
    A->>DB: PaymentEntity(PENDING) 저장
    A->>PG: POST /pg/api/v1/payments
    PG-->>A: {transactionKey, PENDING}
    A->>REG_A: register(transactionKey, innerFuture)
    A-->>C: (HTTP 연결 유지)

    Note over A: Graceful Shutdown 시작<br/>신규 요청 거부<br/>timeout-per-shutdown-phase: 30s

    alt Graceful Shutdown 내 완료 (정상)
        PG->>A: POST /callback {SUCCESS}
        A->>DB: PaymentEntity = SUCCESS, OrderEntity = PAID
        A->>REG_A: pop → innerFuture.complete()
        A-->>C: 200 OK {status: SUCCESS}
    else 강제 종료 or Graceful timeout 초과
        Note over A: 인스턴스 종료<br/>REG_A 소멸 (in-memory 유실)
        A--xC: Connection Reset / 504

        PG->>B: POST /callback {SUCCESS}
        Note over B: Registry 비어있음<br/>Registry.pop() → empty
        B->>DB: [TX3] PaymentEntity = SUCCESS, OrderEntity = PAID
        B-->>PG: 200 OK

        Note over C: 연결 오류 감지 → Client Polling 시작
        C->>B: GET /api/v1/payments/{paymentId}
        B->>DB: findById(paymentId)
        DB-->>B: PaymentEntity(SUCCESS)
        B-->>C: 200 OK {status: SUCCESS}
    end
```

---

## 7. Scheduler 복구 흐름 (미구현 — 추후 commerce-batch)

```mermaid
sequenceDiagram
    participant SCH as PaymentRecoveryScheduler
    participant SVC as PaymentApplicationService
    participant PR as PaymentRepository
    participant OR as OrderRepository
    participant PGC as PgClient
    participant PG as PG Simulator

    loop 30초마다
        SCH->>SVC: recoverPendingPayments()
        SVC->>PR: findAllByStatus(PENDING)
        PR-->>SVC: [PaymentEntity(PENDING), ...]

        loop 각 결제건
            alt transactionKey 존재
                SVC->>PGC: getTransaction(transactionKey)
                PGC->>PG: GET /pg/payments/{transactionKey}
            else transactionKey 없음
                SVC->>PGC: getTransactionByOrderId(orderId)
                PGC->>PG: GET /pg/payments?orderId={orderId}
            end

            PG-->>PGC: {status, reason}
            PGC-->>SVC: result

            alt PG = SUCCESS
                SVC->>PR: PaymentEntity.approve() → save()
                SVC->>OR: OrderEntity.pay() → save()
                Note over SVC: TX 커밋
            else PG = FAILED
                SVC->>PR: PaymentEntity.fail(reason) → save()
                Note over SVC: TX 커밋
            else PG = PENDING
                SVC->>PR: retryCount 증가 → save()
                Note over SVC: retryCount 초과 (30분 기준)<br/>→ PaymentEntity = FAILED<br/>→ 슬랙 알림
            end
        end
    end
```
