# 02. 시퀀스 다이어그램 — 결제 (Payments)

volume-6 결제 기능의 흐름을 레이어별 참여자 기준으로 시각화한다. 표기 규칙(레이어/화살표/생략/공통 에러)은 [`../week2/02-sequence-diagrams.md`](../week2/02-sequence-diagrams.md) §0을 그대로 따른다.

> 본 문서는 구현된 결제 흐름 3종을 담는다 — **결제 시작(pay)** · **콜백 수신(callback, §3.4)** · **Reconcile 대사 배치(§3.5)**. 콜백·Reconcile은 모두 동일한 확정 단위 `PaymentConfirmer.confirm`(비관락+멱등)을 거쳐 "정확히 한 번"만 상태 전이가 일어난다.

## 0. 이 문서의 참여자 (week2 §0.1 레이어에 결제 도메인 추가)

| 약칭 | 클래스 | 레이어 | 책임 |
| --- | --- | --- | --- |
| `PCtrl` | `PaymentV1Controller` | Interface (대고객) | 결제 시작 엔드포인트 (§3.6 예정) |
| `PFac` | `PaymentFacade` | Application | 결제 시작 유스케이스 조립 |
| `UFac` | `UserFacade` | Application | 인증(loginId/Pw → userId) |
| `OSvc` | `OrderService` | Domain Service | 주문 조회·상태 검증 |
| `Ord` | `OrderModel` | Domain Aggregate | 주문(소유자·상태·최종금액) |
| `Pay` | `PaymentModel` | Domain Aggregate | 결제 시도. 카드번호 마스킹·거래키 부여 |
| `PRepo` | `PaymentRepository` | Domain Repository | 결제 영속 (멱등 가드 조회 포함) |
| `PG` | `PgClient` → `PgSimulatorClient` | External | 외부 PG(pg-simulator) 어댑터 (재시도/서킷) |

> **인증 위치 주의** — week2 `OrderV1Controller`는 컨트롤러에서 인증 후 `userId`를 Facade에 넘기지만, 결제는 플랜 §3.3에 따라 **`PaymentFacade.pay`가 `loginId/loginPw`를 받아 내부에서 인증**한다. 본 다이어그램은 구현 그대로(Facade 내부 인증)를 그린다.

---

## UC-P1. 결제 시작 — `POST /api/v1/payments`

주문 생성(placeOrder)과 분리된 별도 진입점. 결제 레코드를 PENDING으로 만들고 PG에 요청해 거래키를 확보한다. **주문 상태는 여기서 건드리지 않으며**, 최종 승인/거절은 PG 콜백 또는 Reconcile이 확정한다.

```mermaid
sequenceDiagram
    actor C as Client(User)
    participant PCtrl as PaymentV1Controller
    participant PFac as PaymentFacade
    participant UFac as UserFacade
    participant OSvc as OrderService
    participant Ord as OrderModel
    participant Pay as PaymentModel
    participant PRepo as PaymentRepository
    participant PG as PgClient(PgSimulatorClient)

    C->>PCtrl: POST /api/v1/payments\n{orderId, cardType, cardNo}
    PCtrl->>PFac: pay(loginId, loginPw, orderId, cardType, cardNo)

    PFac->>UFac: authenticate(loginId, loginPw)
    UFac-->>PFac: userId

    PFac->>OSvc: getOrder(orderId)
    OSvc-->>PFac: OrderModel

    alt 타인 주문 (order.userId != userId)
        PFac-->>C: 404 NOT_FOUND (존재 은닉)
    else 본인 주문
        alt 주문이 PENDING 아님
            PFac-->>C: 409 CONFLICT (결제 불가 상태)
        else PENDING
            PFac->>PRepo: findByOrderId(orderId)
            PRepo-->>PFac: List~PaymentModel~
            Note over PFac: 멱등 가드 — PENDING/SUCCESS 결제가 있으면 중복 결제 차단
            alt 진행 중/완료 결제 존재
                PFac-->>C: 409 CONFLICT
            else 결제 가능
                PFac->>Ord: getFinalAmount()
                Ord-->>PFac: amount

                PFac->>Pay: new PaymentModel(orderId, userId, cardType, cardNo, amount)
                Note over Pay: status=PENDING, cardNo는 마스킹되어 저장
                PFac->>PRepo: save(payment)
                PRepo-->>PFac: PaymentModel(id)

                Note over PFac,PG: PG 호출은 DB 트랜잭션 밖 · @Retry/@CircuitBreaker(40% 일시 500 대응)
                PFac->>PG: requestPayment(orderId, userId, cardType, rawCardNo, amount, callbackUrl)
                PG-->>PFac: PgTransaction{transactionKey, status=PENDING}

                PFac->>Pay: assignTransactionKey(transactionKey)
                PFac->>PRepo: save(payment)
                PRepo-->>PFac: PaymentModel

                PFac-->>C: 200 PaymentInfo{transactionKey, status=PENDING}
            end
        end
    end
```

### 메모
- **카드번호 이중 처리** — `PaymentModel`에는 마스킹본이 저장되고, `PG`에는 원본(`rawCardNo`)이 전달된다(pg-simulator의 카드번호 정규식 검증 통과 목적).
- **트랜잭션 경계** — `PaymentFacade.pay`는 `@Transactional`이 아니며, 각 `save`는 개별 트랜잭션으로 처리된다. PG HTTP 호출이 DB 커넥션/락을 잡지 않도록 트랜잭션 밖에서 일어난다.
- **응답은 PENDING** — pg-simulator는 즉시 PENDING 거래만 발급하고 실제 결과는 1~5초 뒤 비동기로 콜백한다. 따라서 이 흐름의 응답은 항상 PENDING이며, 클라이언트는 콜백 처리 이후의 상태를 별도로 조회한다.

---

## UC-P2. 결제 콜백 수신 — `POST /api/v1/payments/callback`

pg-simulator가 1~5초 뒤 비동기로 보내는 `TransactionInfo`를 수신해 결제·주문을 최종 확정한다. 확정은 콜백·Reconcile이 공유하는 `PaymentConfirmer.confirm`(비관락 `findByTransactionKeyForUpdate` → 멱등 체크 → 상태 전이 → 주문 cascade)으로 일원화돼 있다.

```mermaid
sequenceDiagram
    actor PG as pg-simulator
    participant PCtrl as PaymentV1Controller
    participant PCfm as PaymentConfirmer
    participant PRepo as PaymentRepository
    participant Pay as PaymentModel
    participant OSvc as OrderService

    PG->>PCtrl: POST /callback\nTransactionInfo{transactionKey, status, reason}
    PCtrl->>PCfm: confirm(transactionKey, status, reason)

    Note over PCfm,PRepo: @Transactional — 결제 락 + 주문 확정을 한 트랜잭션으로
    PCfm->>PRepo: findByTransactionKeyForUpdate(transactionKey)
    Note over PRepo: SELECT ... FOR UPDATE (콜백/Reconcile 직렬화)
    PRepo-->>PCfm: PaymentModel

    alt 미존재 거래키
        PCfm-->>PG: 404 NOT_FOUND
    else 이미 확정(SUCCESS/FAILED)
        Note over PCfm: 멱등 — 재반영 없이 현재 상태 반환
    else PENDING
        alt status = SUCCESS
            PCfm->>Pay: markSuccess()
            PCfm->>OSvc: markPaid(orderId)
        else status = FAILED
            PCfm->>Pay: markFailed(reason)
            PCfm->>OSvc: markFailed(orderId)
            Note over OSvc: 재고·쿠폰 원복(cascade)
        end
        PCfm->>PRepo: save(payment)
        Note over PCfm,OSvc: 주문이 이미 다른 경로로 확정 시 CONFLICT → 멱등 skip
    end
    PCfm-->>PG: 200 OK
```

### 메모
- **주문 식별은 콜백 payload를 불신**하고, 우리 결제 레코드의 `orderId`를 신뢰한다(위변조 방지).
- 콜백은 인증 헤더 없이 수신하며 래퍼 없는 `TransactionInfo` 원본을 받는다.

---

## UC-P3. Reconcile 대사 배치 — 스케줄러 + 단념(give-up)

콜백이 유실돼 PENDING으로 남은 결제를 PG 진실원천과 대조해 끝내 확정하는 **안전망 배치**다. 주기 실행(`PaymentReconcileScheduler`)과 운영 수동(`AdminPaymentV1Controller`) 두 트리거가 같은 `PaymentFacade.reconcilePending`을 호출한다. 멀티 인스턴스에서는 **ShedLock**으로 회차당 한 인스턴스만 실행한다.

```mermaid
sequenceDiagram
    participant Sch as PaymentReconcileScheduler
    participant SL as ShedLock(shedlock 테이블)
    participant PFac as PaymentFacade
    participant PRepo as PaymentRepository
    participant PG as PgClient(PgSimulatorClient)
    participant PCfm as PaymentConfirmer

    Note over Sch: @Scheduled(fixedDelay 60s) — 직전 회차 종료 후
    Sch->>SL: 락 획득 시도 (name=paymentReconcile)
    alt 다른 인스턴스가 보유 중
        SL-->>Sch: 실패 → 이번 회차 skip
    else 락 획득
        Sch->>PFac: reconcilePending(page=0, size)
        PFac->>PRepo: findByStatus(PENDING, 0, size)
        Note over PRepo: 오래된 것 우선(id ASC) — 가장 위험한 건이 첫 페이지
        PRepo-->>PFac: List~PaymentModel~

        loop 각 PENDING 결제
            alt 거래키 없음(고아)
                Note over PFac: skip (pay()가 이미 FAILED 정리)
            else 거래키 있음
                PFac->>PG: findTransactionsByOrder(orderId)
                Note over PFac,PG: DB 트랜잭션 밖에서 조회
                PG-->>PFac: List~PgTransaction~ (txKey로 매칭)

                alt PG가 SUCCESS/FAILED
                    PFac->>PCfm: confirm(txKey, status, reason)
                    Note over PCfm: paid / failed 집계
                else PG 미확정(PENDING) 또는 미발견(null)
                    alt 체류시간 ≥ 데드라인(미발견 2분 / PENDING 10분)
                        PFac->>PCfm: confirm(txKey, FAILED, "단념")
                        Note over PCfm: gaveUp — 주문 markFailed(재고·쿠폰 원복)→재결제 가능
                    else 데드라인 전
                        Note over PFac: stillPending — 다음 회차로 미룸
                    end
                end
            end
        end

        PFac-->>Sch: PaymentReconcileResult{scanned,paid,failed,gaveUp,stillPending,skipped}
        Sch->>SL: 락 해제(lockAtLeastFor 경과 후)
    end
```

### 메모
- **무한 PENDING 종료(give-up)** — PG가 끝내 미확정/미발견이어도 체류 데드라인을 넘기면 FAILED로 단념해 종료한다. 이전엔 종료 조건이 없어 60초마다 영원히 재조회했다. 데드라인은 `payment.reconcile.give-up.{not-found-after, pending-after}`로 설정.
- **starvation 방지** — 스캔을 `id ASC`(오래된 것 우선)로 바꿔, PENDING이 페이지 크기를 넘어도 가장 오래(=가장 위험)된 건이 첫 페이지에서 우선 처리된다.
- **확정 경로 단일화** — 정상 확정·give-up 모두 `PaymentConfirmer.confirm`을 거치므로, 늦은 콜백과 경합해도 비관락+멱등으로 한 번만 반영된다(경합 건은 `skipped`).
- **트랜잭션 경계** — `reconcilePending` 자체는 `@Transactional`이 아니며 PG 조회는 DB tx 밖, 각 확정만 `confirm`의 독립 트랜잭션. 한 건 실패가 배치 전체를 막지 않는다.
