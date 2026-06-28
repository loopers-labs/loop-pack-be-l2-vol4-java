# Test Plan

이 문서는 현재 7주차 구현의 테스트 보조 문서다. 제출 커밋에는 포함하지 않는다.

## 목적

- 현재 구현에서 무엇을 우선 검증할지 순서를 정한다.
- 단위 테스트, 통합 테스트, E2E 테스트의 경계를 분리한다.
- 제출용 설계 문서와 구현 테스트가 같은 용어를 쓰도록 맞춘다.

## 우선순위

| 순위 | 영역 | 이유 |
| --- | --- | --- |
| 1 | Kafka 기반 쿠폰 발급 요청과 consumer 처리 | 7주차 핵심인 비동기 선착순 발급의 요청 접수, 발행, 실제 발급, polling 결과가 이어져야 한다. |
| 2 | Outbox relay와 Kafka consumer 멱등성 | At Least Once 발행과 중복 소비 방지가 이벤트 파이프라인의 정합성 기준이다. |
| 3 | product_metrics 집계 갱신 | 좋아요 커맨드 성공과 집계 eventual consistency가 분리되어야 한다. |
| 4 | 주문 생성, 재고 차감, 쿠폰 사용 | 4주차 핵심 트랜잭션 기준이며 7주차 쿠폰 발급 변경 뒤에도 깨지면 안 된다. |

## 기존 확장 검증

아래 항목은 유지하지만 7주차 필수 검증과 분리한다.

| 순위 | 영역 | 이유 |
| --- | --- | --- |
| 1 | 결제 성공/실패/타임아웃 | 주문 상태, 재고, 쿠폰 상태 불일치를 막아야 한다. |
| 2 | 좋아요 등록/취소 | 멱등성과 카운터 정합성이 중요하다. |
| 3 | Outbox/Kafka 전송과 조회 API | 완료 이벤트와 응답 shape가 설계와 맞아야 한다. |

## 단위 테스트

### Product

- 주문 가능 여부를 검증한다.
- 좋아요 가능 여부를 검증한다.
- 재고 차감과 복구가 음수로 내려가지 않는지 확인한다.

### ProductLikeService

- 판매 가능한 상품에만 새 좋아요를 등록하는지 확인한다.
- 중복 좋아요 등록이 멱등 성공으로 처리되는지 확인한다.
- 좋아요 취소가 상품 상태와 무관하게 동작하는지 확인한다.
- `likeCount` 증감이 신규 등록/실제 삭제에만 반응하는지 확인한다.

### StockService

- 비관적 락 기반 재고 차감이 부족 수량을 막는지 확인한다.
- 결제 실패/취소/타임아웃 시 재고 복구가 정확한지 확인한다.

### Coupon

- 정액 할인, 정률 반올림, 할인 상한, 최소 주문 금액을 검증한다.
- 발급 쿠폰의 소유권, 만료, 단일 사용, 복구를 검증한다.
- 삭제·만료 템플릿 발급을 거부하는지 확인한다.
- 비동기 발급 요청 접수 시 `CouponIssueRequest(PENDING)`와 `coupon-issue-requests` Outbox가 함께 저장되는지 확인한다.
- 쿠폰 발급 요청 이벤트 처리 시 `event_handled`를 저장하고 중복 eventId는 실제 발급을 반복하지 않는지 확인한다.
- 전체 발급 한도 초과 요청이 `FAILED`로 확정되고 consumer 메시지는 처리 완료로 기록되는지 확인한다.

### OrderService / PaymentService

- `PAYMENT_PENDING` -> `PAID`, `PAYMENT_FAILED`, `CANCELED` 전이가 맞는지 확인한다.
- `REQUESTED` 결제 row 선생성이 중복 요청을 막는지 확인한다.
- 1분 초과 시 `TIMEOUT`으로 전이되는지 확인한다.

### EventOutbox / EventRelayWorker

- Kafka 발행 성공 시 `SENT`로 바뀌는지 확인한다.
- 실패 시 `retry_count`가 증가하는지 확인한다.
- 최대 재시도 초과 시 `FAILED`로 확정되는지 확인한다.
- relay 성공/실패 count와 duration 지표가 증가하는지 확인한다.

### ProductMetricsConsumer

- `event_handled(event_id PK)`로 중복 이벤트를 skip하는지 확인한다.
- 좋아요 이벤트가 `product_metrics.like_count`를 최신 이벤트 기준으로 갱신하는지 확인한다.
- consumer 성공/실패/중복 처리 지표와 product metrics update 지표가 증가하는지 확인한다.

### CouponIssueRequestConsumer

- `coupon-issue-requests` topic 메시지를 manual Ack로 처리하는지 확인한다.
- `event_handled(event_id PK)`로 중복 발급 이벤트를 skip하는지 확인한다.
- consumer 성공/실패/중복 처리 지표가 증가하는지 확인한다.

## 통합 테스트

### 데이터 계층

- `ProductRepository`가 판매 가능 상품과 숨김 상품을 올바르게 구분하는지 확인한다.
- `ProductLikeRepository`가 `userId + productId` 유니크 제약을 지키는지 확인한다.
- `OrderRepository`, `PaymentRepository`, `EventOutboxRepository`가 상태 기반 조회 조건을 만족하는지 확인한다.

### 트랜잭션 경계

- 주문 생성 시 재고 차감, optional 발급 쿠폰 `USED` 전이, 주문 저장이 같은 트랜잭션에 묶이는지 확인한다.
- 쿠폰 검증 또는 사용 실패 시 먼저 차감한 재고가 롤백되는지 확인한다.
- 결제 실패/취소/타임아웃에서 결제 결과 기록, 주문 상태 전이, 재고 복구가 하나로 묶이는지 확인한다.
- 결제 성공에서 주문 `PAID` 전이와 outbox 저장이 같은 트랜잭션에 묶이는지 확인한다.
- 동일 쿠폰 동시 주문에서 한 건만 성공하고 실패 주문의 재고 차감은 롤백되는지 확인한다.
- 동일 사용자의 동시 발급 요청에서 사용자별 최대 발급 횟수를 지키는지 확인한다.
- Kafka 기반 선착순 발급 요청에서 전체 발급 한도를 초과하지 않는지 확인한다.
- 결제 실패/취소/타임아웃에서 쿠폰이 `AVAILABLE`로 복구되는지 확인한다.
- 0원 주문이 결제 row 없이 즉시 완료되고 outbox를 저장하는지 확인한다.

### 후속 결제 worker 개선

- `REQUESTED -> PROCESSING` 선점이 짧은 DB 트랜잭션에서 처리되는지 확인한다.
- 외부 PG 호출 중에는 DB row lock을 유지하지 않는지 확인한다.
- lease가 만료된 `PROCESSING` 결제를 다시 처리할 수 있는지 확인한다.
- 성공, 실패, 취소, timeout 결과가 동시에 들어와도 결제와 주문 상태가 한 번만 확정되는지 확인한다.

## E2E 테스트

### 핵심 시나리오

- 상품 상세 조회
- 좋아요 등록과 취소
- 주문 생성 후 `PAYMENT_PENDING` 응답
- 결제 성공 후 주문 상태 조회
- 결제 실패/취소/타임아웃 후 주문 상태 조회
- outbox 재시도 후 Kafka 전송 성공
- 고객 쿠폰 발급 요청과 polling 상태 조회
- Kafka consumer 처리 후 내 쿠폰 목록 조회
- ADMIN 쿠폰 템플릿 CRUD와 발급 이력 조회
- 쿠폰 적용 주문의 할인 금액 응답

### 실패 시나리오

- 재고 부족 주문
- 판매 중지/품절 상품 좋아요 등록
- 중복 좋아요 등록
- 1분 초과 결제 처리
- Kafka 전송 실패 후 재시도 초과

## 준비 데이터

| 데이터 | 용도 |
| --- | --- |
| 브랜드 1개 | 상품 목록, 상품 상세 |
| 판매 가능 상품 1개 | 좋아요, 주문, 결제 성공 |
| 품절 상품 1개 | 조회 제외, 좋아요 정책 |
| 판매 중지 상품 1개 | 조회 제외, 좋아요 정책 |
| 사용자 1명 | 좋아요, 주문, 상태 조회 |

## 현재 검증 기준

1. 도메인 규칙과 application orchestration은 Fake/Stub 기반 focused test로 빠르게 검증한다.
2. repository adapter와 JPA 매핑은 Testcontainers 기반 통합/E2E 환경에서 검증한다.
3. 현재 로컬 환경에서 Docker/Testcontainers가 없으면 E2E 실패는 환경 이슈로 기록하고, `compileTestJava`와 E2E 제외 focused test를 최소 검증선으로 둔다.
