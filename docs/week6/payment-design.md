# 6주차 — 결제 도메인 설계

## 1. 상태 머신

```
[ 사용자 요청 ]
       │
       ▼
   PENDING ─────────────────────────────┐
       │                                 │
       │ (PG 응답 200 + transactionKey)  │ (PG 타임아웃 / 회로 차단 / 5xx)
       ▼                                 ▼
   REQUESTED                       TIMEOUT_PENDING
       │                                 │
       │ (콜백 SUCCESS or PG GET)        │ (복구 폴링 — orderId 로 PG 검색)
       │ (콜백 FAILED  or PG GET)        │
       ▼                                 ▼
   SUCCEEDED / FAILED  ◄─────────  SUCCEEDED / FAILED
       │ (종료)                         │ (종료)
       ▼                                 ▼
      ─────  (이후 전이 없음)  ─────
```

| 상태 | 의미 | 다음 가능 상태 |
|------|------|--------------|
| `PENDING` | 결제 레코드 생성, 아직 PG 호출 전 | `REQUESTED`, `TIMEOUT_PENDING` |
| `REQUESTED` | PG 가 요청을 접수하고 `transactionKey` 발급. 처리 결과 대기 중 | `SUCCEEDED`, `FAILED` |
| `TIMEOUT_PENDING` | 우리쪽 타임아웃/회로 차단으로 PG 응답을 못 받음 — `transactionKey` 가 없을 수 있음 | `SUCCEEDED`, `FAILED`, (계속 `TIMEOUT_PENDING`) |
| `SUCCEEDED` | 결제 성공 확정 (콜백 또는 복구 폴링으로) | — (종료) |
| `FAILED` | 결제 실패 확정 (한도 초과 / 잘못된 카드 / 거절) | — (종료) |

## 2. 트랜잭션 경계

핵심 원칙: **PG 외부 호출은 어떤 DB 트랜잭션에도 포함되지 않는다**.

```
[Facade.request]                       Tx 경계
    │
    ├─ PaymentService.createPending    [TX1] REQUIRES_NEW  ── 커밋 ──
    │   - PENDING 레코드 INSERT
    │
    ├─ PgClient.request(...)           (트랜잭션 밖, 외부 호출)
    │
    └─ (응답 종류에 따라 분기)
        ├─ 성공: markRequested        [TX2] REQUIRES_NEW  ── 커밋 ──
        └─ 실패: markTimeoutPending   [TX2] REQUIRES_NEW  ── 커밋 ──
```

이렇게 쪼개는 이유:

- **외부 호출 시간 동안 DB 락이 잡히지 않는다** — PG 처리 지연이 1~5초인데, 단일 트랜잭션이면 그 시간 내내 row lock + connection 점유.
- **외부 호출 실패해도 PENDING 흔적은 남는다** — 사용자에겐 "결제 처리 중" 으로 응답, 운영자/스케줄러는 추적 가능.
- **외부 성공 + 내부 커밋 실패** 상황을 어색하지만 분리 가능 — TX2 가 실패해도 TX1 의 PENDING + 외부 transactionKey 는 살아 있어 복구 폴링이 마무리.

## 3. 멱등성

| 호출 지점 | 멱등키 | 보장 수단 |
|---------|------|---------|
| `POST /api/v1/payments` | `orderId` | DB `UNIQUE(order_id)` + `createPending` 의 `DataIntegrityViolationException` 흡수 |
| `PgClient.request` | `orderId` (PG 시뮬레이터 명세) | PG 가 같은 orderId 면 같은 transactionKey 반환한다고 가정 |
| `POST /api/v1/payments/callback` | `transactionKey` + 도메인 상태 전이 검사 | `markSucceeded` 는 이미 SUCCEEDED 면 통과, `markFailed` 도 동일 |
| 복구 폴링 → `applyExternalStatus` | `paymentId` + 도메인 상태 검사 | 위와 동일 — 종료 상태 결제는 다시 전이 안 됨 |

## 4. 책임 분리

| 레이어 | 책임 |
|--------|------|
| `interfaces.api.payment.PaymentV1Controller` | HTTP 변환만. 인증 (현 단계 헤더 단순화) |
| `application.payment.PaymentFacade` | 유스케이스 조립. 트랜잭션 분리 + PG 호출 + 도메인 협력 |
| `application.payment.PaymentRecoveryService` | 콜백 유실 보상. 주기적 PG GET 폴링 |
| `domain.payment.PaymentModel` | 상태 머신 + 불변식 |
| `domain.payment.PaymentService` | 작은 트랜잭션 단위로 상태 전이 |
| `domain.payment.PgClient` | 외부 의존성 추상화 (포트) |
| `infrastructure.payment.PgClientAdapter` | Resilience4j 적용 + Feign 호출 변환 |
| `infrastructure.payment.PgFeignClient` | 실제 HTTP 호출 |
