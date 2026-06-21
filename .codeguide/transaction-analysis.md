# Transaction Analysis Guide

## 목적

4주차 필수 범위인 주문, 쿠폰, 재고 흐름의 트랜잭션 경계와 락 획득 순서를 우선 점검한다.

결제 worker와 Outbox는 기존 확장 설계로 분리해 점검한다.

## 주문 생성

`OrderFacade.placeOrder()`의 단일 DB 트랜잭션에서 4주차 필수 구간은 아래 순서를 유지한다.

```text
1. 상품 ID 오름차순으로 상품 row PESSIMISTIC_WRITE 조회
2. 재고 검증 및 차감
3. 발급 쿠폰 ID가 있으면 issued_coupon row PESSIMISTIC_WRITE 조회
4. 쿠폰 소유권, 상태, 만료, 최소 주문 금액 검증 후 USED 전이
5. 주문 스냅샷 저장
```

재고 차감, 쿠폰 사용, 주문 저장 중 하나라도 실패하면 전체 롤백한다. 현재 구현은 주문 저장 뒤 기존 확장 분기를 이어간 후 facade 트랜잭션을 commit한다.

## 쿠폰 발급

`CouponCommandService.issue()`는 템플릿 row를 `PESSIMISTIC_WRITE`로 조회한다.

- soft delete 또는 만료 템플릿은 발급하지 않는다.
- 같은 트랜잭션에서 사용자별 기존 발급 수를 확인한다.
- `maxIssuesPerUser` 미만일 때만 발급 쿠폰을 저장한다.

## 기존 확장 설계

현재 구현은 주문 생성 이후 결제와 Outbox까지 처리한다. 아래 내용은 4주차 필수 범위가 아니라 기존 확장 동작의 점검 기준이다.

### 주문 생성 후 분기

```text
1. finalAmount > 0이면 REQUESTED 결제 row 저장
2. finalAmount == 0이면 결제 row 없이 PAID 전이와 ORDER_PAID outbox 저장
3. 0원 주문 조회 응답은 PaymentStatus.NOT_REQUIRED로 계산
```

### 실패 복구

결제 실패, 취소, timeout은 하나의 트랜잭션에서 처리한다.

```text
1. 주문 row 잠금
2. 상품 ID 오름차순 재고 row 잠금 및 복구
3. 발급 쿠폰 ID가 있으면 issued_coupon row 잠금 및 AVAILABLE 복구
4. 주문 실패 상태 저장
```

### 결제 worker 개선 목표

현재 `PaymentWorker.processRequestedPayments()`는 `Payment` row lock을 유지하는 트랜잭션 안에서 외부 PG를 호출한다. 아래 구조로 후속 개선한다.

```text
1. 짧은 DB 트랜잭션에서 REQUESTED -> PROCESSING 선점과 lease 만료시각 저장
2. DB 트랜잭션 밖에서 외부 PG authorize/capture/void 호출
3. 별도 DB 트랜잭션에서 payment row와 order row를 잠그고 결과 반영
4. lease 만료 PROCESSING 건은 worker 중단 건으로 보고 재처리
```

`PaymentStatus.PROCESSING`은 6주차 PG 요청 접수 상태로 코드에 반영했다. lease 만료시각은 목표 구조이며 현재 코드에는 아직 반영되지 않았다.

## 점검 체크리스트

- 재고 차감, 쿠폰 사용, 주문 저장이 하나의 DB 트랜잭션인가?
- 재고와 쿠폰 락 획득 순서가 주문 생성과 실패 복구에서 동일한가?
- 단순 조회 메서드에 `@Transactional(readOnly = true)`가 적용되었는가?
- 발급 쿠폰 만료 상태를 DB 갱신 없이 조회 시 계산하는가?
- 동일 상품, 동일 쿠폰, 동일 사용자 발급 요청의 동시성 테스트가 존재하는가?
- 외부 결제 API 호출이 DB 트랜잭션 내부에 포함되지 않았는가?
- 결제 결과 반영이 상태 확인과 row lock을 통해 한 번만 확정되는가?
