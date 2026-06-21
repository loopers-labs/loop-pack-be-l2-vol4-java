# Design Review

이 문서는 현재 4주차 구현의 설계/구현 정합성 검토 문서다. 제출 커밋에는 포함하지 않는다.

## 검토 기준

- 현재 주차는 4주차 구현 단계다.
- `.docs/design`의 4개 파일은 volume-2 설계 이력으로 유지한다.
- `.docs/design`은 현재 4주차 구현 기준 문서가 아니므로 쿠폰 설계를 덮어쓰지 않는다.
- 4주차 필수 검토 범위는 쿠폰 발급, 쿠폰 사용, 재고 차감, 주문 저장의 원자성과 동시성 제어다.
- 결제 worker, Outbox, 0원 주문 처리는 기존 구현 확장이며 4주차 필수 범위와 분리해 검토한다.
- 목표 아키텍처는 5계층 우선 패키지 구조 안에 도메인 모듈 경계를 두는 모듈러 모놀리스다.
- 현재 구현에는 `Point`/포인트 도메인을 포함하지 않는다.
- 구현 대상 도메인은 JPA/Spring 의존이 없는 POJO 도메인 엔티티와 infrastructure `*JpaEntity`를 분리한다.
- 포인트는 제외하고, 독립 `coupon` 모듈의 템플릿·발급·사용·복구를 구현한다.

## 4주차 핵심 설계

| 영역 | 판단 |
| --- | --- |
| 쿠폰 모듈 | 기존 모듈에 포함하지 않고 독립 `coupon` 모듈로 추가했다. |
| 쿠폰 발급 동시성 | 템플릿 row를 `PESSIMISTIC_WRITE`로 잠근 뒤 사용자별 발급 수를 검사하고 발급 쿠폰을 저장한다. |
| 주문 동시성 | 상품 ID 오름차순으로 상품 row를 잠그고 재고를 차감한 뒤, 발급 쿠폰 row를 `PESSIMISTIC_WRITE`로 잠가 사용 처리한다. |
| 주문 원자성 | 재고 차감, 쿠폰 `USED` 전이, 주문 저장은 `OrderFacade.placeOrder()`의 단일 DB 트랜잭션에 포함한다. |
| 실패 롤백 | 재고 차감 또는 쿠폰 검증/사용이 실패하면 주문 관련 변경을 모두 롤백한다. |
| 주문 할인 스냅샷 | 할인 전 금액, 할인 금액, 최종 금액, nullable 발급 쿠폰 ID를 주문에 저장한다. |
| 쿠폰 식별자 | 주문 요청의 nullable `couponId`는 쿠폰 템플릿 ID가 아니라 사용자가 보유한 발급 쿠폰 ID다. |
| 테스트 계획 | 동일 상품 동시 주문, 동일 발급 쿠폰 동시 사용, 동일 사용자 동시 발급 한도, 실패 롤백을 필수 검증 대상으로 둔다. |

## 기존 확장 설계

| 영역 | 현재 기준 |
| --- | --- |
| 결제 | 주문 금액이 0원보다 크면 `Payment(REQUESTED)`를 만들고 내부 worker가 외부 PG를 호출한다. |
| 0원 주문 | 현재 구현은 결제 row 없이 즉시 `PAID`, 응답 `PaymentStatus.NOT_REQUIRED`, `ORDER_PAID` outbox 저장으로 처리한다. 4주차 필수 정책은 아니다. |
| Outbox | 주문 `PAID` 전이와 `ORDER_PAID` outbox 저장을 같은 DB 트랜잭션으로 처리한다. |
| 결제 복구 | 결제 실패, 취소, timeout 시 주문 row, 상품 row, 발급 쿠폰 row 순서로 잠그고 재고와 쿠폰을 복구한다. |

## 빠진 부분 또는 보강 필요

| 우선순위 | 항목 | 이유 | 반영 위치 |
| --- | --- | --- | --- |
| 1 | DB 기반 동시성 검증 | 로컬 Docker/MySQL 부재로 실제 비관적 락과 롤백 통합 테스트를 완료하지 못했다. | `.docs/test-plan.md`, `.docs/worklog.md` |
| 2 | 결제 worker 트랜잭션 축소 | 현재 worker는 `Payment` row lock을 유지한 채 외부 PG를 호출한다. 외부 호출을 DB 트랜잭션 밖으로 옮겨야 한다. | `.codeguide/transaction-analysis.md`, `.docs/architecture.md` |
| 3 | 결제 선점 상태 | 외부 호출 분리 시 중복 worker와 worker 중단을 처리할 `PROCESSING + lease` 상태가 필요하다. 현재 코드에는 미반영이다. | `.codeguide/transaction-analysis.md`, `.docs/architecture.md` |

## 처리 순서

1. Docker 또는 MySQL이 있는 환경에서 4주차 필수 동시성 테스트를 실행한다.
2. 결제 worker를 짧은 선점 트랜잭션, 트랜잭션 밖 PG 호출, 별도 결과 반영 트랜잭션으로 분리한다.
3. `PaymentStatus.PROCESSING`과 lease 만료시각을 추가하고 worker 중단 후 재처리를 검증한다.

## 다음 질문 후보

- 0원 주문의 payment row 저장 정책은 결제 worker 개선 작업을 시작하기 전에 별도로 확정한다.
