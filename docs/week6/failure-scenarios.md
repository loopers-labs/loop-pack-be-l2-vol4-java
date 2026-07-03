# 6주차 — 결제 장애 시나리오 분석

> `analyze-external-integration` Skill 적용. 정상 흐름보다 실패 흐름을 우선해서 본다.

## 시나리오 매트릭스

### S1. PG 가 요청 직후 5xx 반환

| 항목 | 내용 |
|------|------|
| 트리거 | PG 가 즉시 500 응답 |
| 우리쪽 동작 | Retry 3회 → 다 실패 → 폴백 → `PgRequestException` → Facade 가 `TIMEOUT_PENDING` 으로 전이 |
| 데이터 정합성 | 결제 PENDING/TIMEOUT_PENDING. 주문은 그대로 PENDING (아직 결제 미확정) |
| 사용자에게 보이는 응답 | `status=TIMEOUT_PENDING` — "처리 중" 으로 표시 가능 |
| 복구 가능성 | 복구 스케줄러가 5초마다 PG `findByOrderId` 폴링 → 발견 시 동기화. PG 가 영원히 5xx 면 결제는 영원히 TIMEOUT_PENDING 잔존 → 운영자 수동 처리 |

### S2. PG 응답이 우리쪽 read timeout (1500ms) 초과

| 항목 | 내용 |
|------|------|
| 트리거 | 네트워크 지연 또는 PG 부하 |
| 우리쪽 동작 | S1 과 동일 — 폴백 |
| 데이터 정합성 | **위험**: PG 가 실제로 요청을 받았지만 응답만 늦은 경우. PG 측에는 transactionKey 가 생성됐는데 우리는 모른다 |
| 복구 가능성 | `findByOrderId` 폴링이 PG 가 만든 transactionKey 를 발견 → `attachTransactionKey` 로 보강 → 처리 결과(SUCCESS/FAILED) 동기화 |
| 남는 리스크 | 첫 복구 폴링까지 5~10초 동안 사용자는 결과를 모름 |

### S3. PG 처리 성공 후 콜백 유실

| 항목 | 내용 |
|------|------|
| 트리거 | PG 가 콜백 발송했으나 우리쪽 네트워크/서비스 다운으로 수신 실패 |
| 우리쪽 동작 | 결제는 `REQUESTED` 상태로 머문다 (PG 가 SUCCESS 처리한지 모름) |
| 데이터 정합성 | 결제 REQUESTED, 주문 PENDING. 사용자는 "결제 처리 중" 표시 |
| 복구 가능성 | `REQUESTED` 인 결제 중 마지막 갱신이 30초 이전이면 복구 대상에 포함 → `getByTransactionKey` 로 PG GET → SUCCESS 발견 → 동기화 |
| 남는 리스크 | 첫 30초 동안 사용자가 본 상태와 PG 실제 상태가 어긋남 |

### S4. 회로 차단된 상태에서 결제 요청

| 항목 | 내용 |
|------|------|
| 트리거 | 직전 20건 중 50% 가 실패해 CB 가 OPEN. 10초 wait |
| 우리쪽 동작 | `CallNotPermittedException` → 즉시 폴백 → `TIMEOUT_PENDING` |
| 데이터 정합성 | 결제 TIMEOUT_PENDING. 주문 PENDING |
| 복구 가능성 | 회로가 닫힌 뒤 (10초 + half-open 3건 성공) 다음 복구 폴링이 PG 와 동기화 |
| 남는 리스크 | 회로 차단 동안 PG 가 정상이어도 우리는 호출 안 함 — 의도된 보호 |

### S5. PG 가 SUCCESS 콜백 후 똑같은 콜백 재전송

| 항목 | 내용 |
|------|------|
| 트리거 | PG 콜백 재시도 정책 |
| 우리쪽 동작 | 첫 콜백: `markSucceeded`. 두 번째: 이미 SUCCEEDED 라 멱등 통과 |
| 데이터 정합성 | 결제 SUCCEEDED, 주문 PAID (둘 다 한 번만 전이) |
| 복구 가능성 | 해당 없음 |
| 남는 리스크 | 없음 — 도메인 메서드가 멱등 보장 |

### S6. PG 가 SUCCESS 콜백 보냈는데 우리는 이미 FAILED 로 마감한 상황

| 항목 | 내용 |
|------|------|
| 트리거 | 복구 폴링 타이밍 + 콜백 순서 어긋남으로 동시 진입 |
| 우리쪽 동작 | `markSucceeded` 시도 → 도메인이 `CONFLICT` 예외 발생 |
| 데이터 정합성 | 결제는 FAILED 로 유지 — 사용자 경험은 일관됨. 그러나 PG 측 잔액은 SUCCESS 라 자금 불일치 |
| 복구 가능성 | 코드 자동 복구 불가 — 알람 + 운영자 환불 절차 필요 |
| 남는 리스크 | **이게 가장 위험한 시나리오**. 실제 운영에선 콜백 수신과 폴링이 동시 진행되지 않도록 paymentId 단위 락 또는 큐 직렬화 검토 필요 |

### S7. createPending 직후 앱이 죽음

| 항목 | 내용 |
|------|------|
| 트리거 | PENDING 결제 INSERT 직후 OOM/Kill |
| 우리쪽 동작 | PENDING 결제가 DB 에 남아 있음. PG 호출 전이라 PG 측엔 흔적 없음 |
| 데이터 정합성 | 결제 PENDING (영원히). 주문 PENDING |
| 복구 가능성 | 현재 복구 스케줄러는 `TIMEOUT_PENDING`/`REQUESTED` 만 대상 — **PENDING 은 누락** |
| 남는 리스크 | 본 PR 한계. 후속 PR 에서 `PENDING` 도 일정 시간 경과 시 복구 대상에 포함 또는 사용자 재시도로 위임 |

## 종합 — 가장 먼저 손대야 할 위험

1. **S6 (콜백 vs 복구 폴링 동시 진입)** — paymentId 단위 락 또는 비관적 락 검토
2. **S7 (PENDING 정체)** — 복구 대상에 PENDING 포함 또는 사용자 재시도 정책 명문화
3. **S3 (콜백 30초 갭)** — 복구 주기 단축 + 사용자 UX 명시 ("결제 처리 중" 안내)

## 미룰 수 있는 것

- HMAC 콜백 서명 검증 (시뮬레이터 한계)
- 멀티 인스턴스 환경의 분산 락 (운영 인스턴스 1대 가정)
- 이벤트 기반 외부 통합 (Outbox 패턴) — 현 동기 + 폴링 조합이 학습 단계엔 충분
