# 4주차 트랜잭션 체크리스트

이 문서는 Loopers 4주차 트랜잭션 작업에서 사용할 프로젝트 전용 기준이다.

## 4주차 필수 범위

- 주문, 재고, 쿠폰의 RDB 트랜잭션 정합성과 동시성 제어를 우선한다.
- 재고 차감과 쿠폰 사용은 현재 기준상 비관적 row lock을 기본 전략으로 본다.
- Redis, Kafka, cache, message broker, Flyway는 사용자 명시 승인 없이 도입하지 않는다.
- 패키지는 `interfaces`, `application`, `domain`, `infrastructure`, `support` 5계층을 유지하고 하위에 `catalog`, `coupon`, `ordering`, `payment`, `event` 도메인 경계를 둔다.

## 주문 생성

`OrderFacade.placeOrder()`를 4주차 필수 주문 흐름의 애플리케이션 트랜잭션 경계로 본다.

하나의 DB 트랜잭션 안에서 아래 순서를 유지한다.

1. 상품 row를 상품 ID 오름차순으로 `PESSIMISTIC_WRITE` 조회한다.
2. 판매 상태와 재고를 검증한다.
3. 재고를 차감한다.
4. 발급 쿠폰 ID가 있으면 발급 쿠폰 row를 `PESSIMISTIC_WRITE` 조회한다.
5. 쿠폰 소유권, 상태, 만료, 최소 주문 금액을 검증한다.
6. 발급 쿠폰을 `USED`로 전이한다.
7. `originalAmount`, `discountAmount`, `finalAmount`, nullable 발급 `couponId`를 포함해 주문과 주문 항목 스냅샷을 저장한다.

재고 차감, 쿠폰 사용, 주문 저장 중 하나라도 실패하면 주문 생성 트랜잭션 전체가 롤백되어야 한다.

## 쿠폰 발급

`CouponCommandService.issue()`는 아래 작업을 하나의 트랜잭션에 포함해야 한다.

1. 쿠폰 템플릿 row를 `PESSIMISTIC_WRITE`로 조회한다.
2. soft delete 또는 만료 템플릿을 거부한다.
3. 같은 템플릿에 대한 사용자별 기존 발급 수를 확인한다.
4. `maxIssuesPerUser` 미만일 때만 발급 쿠폰을 저장한다.

## 기존 확장 설계

아래 흐름은 4주차 필수 범위와 분리해서 보되, 정합성은 계속 점검한다.

- 유상 주문은 `finalAmount > 0`일 때 주문 생성 트랜잭션에서 `PaymentStatus.REQUESTED` 결제 row를 저장한다.
- 0원 주문의 현재 구현은 결제 row 없이 즉시 `PAID`로 전이하고 `ORDER_PAID` outbox를 저장하며, 조회 응답의 결제 상태는 `PaymentStatus.NOT_REQUIRED`로 계산한다.
- 결제 실패, 취소, timeout은 하나의 DB 트랜잭션에서 주문 row 잠금, 상품 ID 오름차순 상품 row 잠금 및 재고 복구, optional 발급 쿠폰 row 잠금 및 `AVAILABLE` 복구, 주문 실패/취소 상태 저장 순서로 처리한다.
- 주문 `PAID` 전이와 `ORDER_PAID` outbox 저장은 같은 DB 트랜잭션에 포함하고, 외부 데이터 플랫폼 전송은 트랜잭션 밖에서 처리한다.

## 결제 Worker 개선 목표

현재 알려진 위험은 `PaymentWorker.processRequestedPayments()`가 payment row lock을 유지한 DB 트랜잭션 안에서 외부 PG를 호출할 수 있다는 점이다.

목표 구조는 아래와 같다.

1. 짧은 DB 트랜잭션에서 `REQUESTED -> PROCESSING` 선점과 lease 만료시각을 저장한다.
2. 외부 PG `authorize`, `capture`, `void` 호출은 DB 트랜잭션 밖에서 수행한다.
3. 별도 DB 트랜잭션에서 payment row와 order row를 잠그고 결과를 한 번만 반영한다.
4. lease가 만료된 `PROCESSING` row는 worker 중단 건으로 보고 재처리한다.

`PaymentStatus.PROCESSING`과 lease 만료시각은 목표 구조이며 현재 코드에는 없을 수 있다.

## 확인할 테스트

- 동일 상품 동시 주문에서 재고가 정확히 차감되고 음수가 되지 않는다.
- 동일 발급 쿠폰 동시 주문에서 쿠폰은 한 번만 사용된다.
- 재고 차감, 쿠폰 사용, 주문 저장은 실패 시 함께 롤백된다.
- 동일 사용자 쿠폰 동시 발급은 `maxIssuesPerUser`를 초과하지 않는다.
- 조회 흐름과 쓰기 트랜잭션 흐름은 별도로 검증된다.
- 결제 실패, 취소, timeout 처리에서 재고와 쿠폰이 하나의 트랜잭션으로 복구된다.
