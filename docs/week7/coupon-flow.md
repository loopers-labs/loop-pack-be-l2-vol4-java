# 7주차 — 선착순 쿠폰 발급 흐름 (Kafka + 동시성)

## 1. 요구사항

- 발급 수량 제한 (예: 선착순 100명)
- 초과 발급 절대 금지
- 같은 사용자 중복 발급 금지
- API 는 요청 접수만 하고 실제 발급은 비동기
- 사용자는 결과를 폴링으로 조회

## 2. 전체 흐름

```
[Client]  POST /api/v1/coupons/{couponTemplateId}/issue-requests
    │        Header: X-Loopers-User-Id: 100
    ▼
[Facade]  CouponIssueRequestFacade.enqueue
    │  1. 템플릿 존재 검증
    │  2. UUID requestId 발급
    │  3. INSERT coupon_issue_requests (status=PENDING) │─┐
    │  4. publishEvent(CouponIssueRequestedEvent)      │ │ 같은 tx
    │       └─► OutboxEventListener                    │ │ (Transactional Outbox)
    │            └─► INSERT outbox_message             │ │
    │  5. 202 Accepted { requestId, status=PENDING }   │─┘
    │
    ▼
[Relay]  OutboxRelay (@Scheduled 1s)
    │  SELECT PENDING ... → Kafka send → mark SENT
    │
    ▼
[Kafka]  coupon-issue-requests (파티션 키 = couponTemplateId)
    │  → 같은 쿠폰 요청들은 <b>단일 파티션</b>에 몰림
    │
    ▼
[Consumer]  CouponIssueRequestConsumer (group=commerce-api-coupon)
    │  파티션당 단일 스레드 처리 → 순차 처리
    │
    ▼
[Domain]  FirstComeIssueService.processRequest(requestId) 한 tx
    │  1. request 조회 (이미 처리됐으면 종료)
    │  2. template 조회
    │  3. 만료 체크 → REJECTED_INVALID
    │  4. 중복 발급 체크 (existsByUserIdAndCouponTemplateId) → REJECTED_DUPLICATE
    │  5. template.issueOne() → CONFLICT 발생 시 REJECTED_SOLD_OUT
    │  6. UserCoupon INSERT
    │  7. request.markIssued(userCouponId) — 상태 전이
    ▼
[Client]  GET /api/v1/coupons/issue-requests/{requestId}  (polling)
    └─► { status: ISSUED|REJECTED_*, userCouponId, rejectReason }
```

## 3. 동시성 제어 — 파티션 순차 처리 + 도메인 CAS

### 3.1 파티션 순차 처리

파티션 키를 `couponTemplateId` 로 지정 → 같은 쿠폰의 모든 요청이 <b>같은 파티션</b>으로 라우팅.
Consumer 그룹의 파티션 하나는 단일 스레드가 소비 → 요청들이 <b>도착 순서대로 순차 처리</b>.

### 3.2 도메인 CAS

```java
public void issueOne() {
    if (totalStock == null) { issuedCount += 1; return; }        // 무제한
    if (issuedCount >= totalStock) {
        throw new CoreException(CONFLICT, "쿠폰이 모두 소진되었습니다.");
    }
    issuedCount += 1;
}
```

파티션 순차 처리 덕에 락 없이 순수 도메인 규칙으로 초과 발급 방지 가능.

### 3.3 중복 발급 방지

`user_coupons` 에 `existsByUserIdAndCouponTemplateId` 로 확인. UNIQUE 제약을 걸어도 되지만, 4주차의 다른 유스케이스(관리자 수동 발급 등)와 충돌하지 않도록 UNIQUE 대신 존재 체크만 사용.

## 4. 응답 상태

| status | 의미 | userCouponId | rejectReason |
|---|---|---|---|
| PENDING | 큐에 대기 중 | null | null |
| ISSUED | 발급 성공 | 신규 UserCoupon.id | null |
| REJECTED_SOLD_OUT | 재고 소진 | null | "쿠폰이 모두 소진되었습니다." |
| REJECTED_DUPLICATE | 중복 발급 | null | "이미 발급받은 쿠폰입니다." |
| REJECTED_INVALID | 만료된 쿠폰 등 | null | "만료된 쿠폰입니다." |

## 5. 멱등성

3 층 방어:

1. **Kafka Producer 멱등**: `enable.idempotence=true` — 브로커 재시도 중복 방지
2. **Consumer 재수신 멱등**: `FirstComeIssueService` 가 `request.getStatus().isTerminal()` 이면 이전 결과 반환
3. **요청 접수 멱등**: (미적용) — 클라이언트가 같은 요청을 두 번 보내면 두 개의 requestId 가 생김. 후속 PR 에서 클라이언트 request-key 지원 검토.

## 6. 테스트

- `CouponTemplateStockTest`: issueOne / isSoldOut / 무제한 / 초과 발급 방지
- `FirstComeIssueServiceTest`:
  - 첫 발급 성공
  - 재고 소진 REJECTED_SOLD_OUT
  - 중복 발급 REJECTED_DUPLICATE (재고에 영향 없음)
  - 같은 requestId 재처리 멱등
  - <b>정원 100 / 요청 200 → 정확히 100 발급</b>
- `CouponIssueRequestFacadeTest`: enqueue 시 CouponIssueRequestedEvent 발행

## 7. 안 한 것 / 이유

| 미적용 | 이유 |
|---|---|
| Redis 기반 재고 카운터 | Kafka 파티션 순차 처리 + 단일 DB CAS 로 충분. 처리량 병목 관측 후 도입 검토 |
| 웹훅 콜백 결과 통보 | 학습 단계엔 폴링으로 충분. 사용자에게 웹훅 endpoint 요구는 과함 |
| 발급 실패 시 Kafka DLQ | 사실상 비즈니스 로직으로 REJECTED_* 결과 저장 — 시스템 실패는 아님 |
| 쿠폰 발급 히스토리 UI | UI 는 별도 이슈 |
