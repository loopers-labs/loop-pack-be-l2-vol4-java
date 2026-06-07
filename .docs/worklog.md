# Worklog Snapshot

이 문서는 현재 4주차 구현 작업을 이어가기 위한 최신 스냅샷이다.
누적 로그가 아니라 현재 상태만 유지한다.

## 현재 상태

| 항목 | 내용 |
| --- | --- |
| 날짜 | 2026-06-02 |
| 브랜치 | `volume-3` |
| 현재 단계 | 4주차 설계 기준 보정 완료, DB 기반 동시성 검증 대기 |
| 구현 범위 | `apps/commerce-api` RDB-only |
| 제외 범위 | Point, Redis, Kafka, cache, message broker, Flyway 도입 제외 |
| 모듈 경계 | `catalog`, `coupon`, `ordering`, `payment`, `event` |
| 패키지 구조 | `interfaces`, `application`, `domain`, `infrastructure`, `support` 5계층 하위에 도메인 경계를 둠 |
| 지속성 기준 | MySQL/JPA 기반 RDB 저장소와 outbox |

## 최근 결정

- `.docs/design/*` 4개 파일은 volume-2 제출 이력으로 보존하고 현재 4주차 기준으로 덮어쓰지 않는다.
- 4주차 필수 설계는 재고, 쿠폰, 주문의 RDB 트랜잭션 정합성과 동시성 제어다.
- 결제 worker, Outbox, 0원 주문 처리는 기존 확장 설계로 분리해 관리한다.
- 쿠폰은 각 계층 하위의 독립 `coupon` 모듈로 둔다.
- 쿠폰 템플릿은 이름, `FIXED`/`RATE`, 할인값, optional 최소 주문 금액, 사용자별 최대 발급 횟수, 미래 만료일을 가진다.
- 템플릿 전체 발급 수량 제한은 두지 않는다.
- 발급 쿠폰은 할인 조건과 만료일을 스냅샷으로 저장하고, 이름은 최신 템플릿 값을 참조한다.
- 템플릿 삭제는 soft delete이며 기존 발급 쿠폰은 유지한다. ADMIN 조회에는 삭제 템플릿도 노출한다.
- 쿠폰 발급은 템플릿 row를 `PESSIMISTIC_WRITE`로 잠그고 사용자별 발급 한도를 검사한다.
- 주문은 상품 ID 오름차순 재고 락과 차감 후 발급 쿠폰 row를 `PESSIMISTIC_WRITE`로 잠그고 사용 처리한다.
- 정률 할인은 1~100%이며 원 단위 반올림한다. 최소 주문 금액은 할인 전 합계 기준이고 할인액 상한은 주문 금액이다.
- 주문 스냅샷은 `originalAmount`, `discountAmount`, `finalAmount`, nullable `couponId`를 저장한다.
- 모든 0원 주문은 결제 row 없이 즉시 `PAID` 처리하고 `ORDER_PAID` outbox를 저장한다. 조회 결제 상태는 `NOT_REQUIRED`다.
- 결제 실패, 취소, timeout 시 재고와 쿠폰을 함께 복구한다.
- 현재 결제 worker는 `Payment` row lock을 유지한 트랜잭션 안에서 외부 PG를 호출한다.
- 후속 개선은 짧은 트랜잭션에서 `REQUESTED -> PROCESSING` 선점과 lease 만료시각을 저장하고, 외부 PG 호출과 결과 반영 트랜잭션을 분리하는 방식으로 진행한다.
- `PaymentStatus.PROCESSING`, lease 만료시각, 0원 주문의 payment row 저장 정책은 후속 개선 전에 확정하거나 구현해야 한다.
- 운영 DDL 문서와 Flyway는 추가하지 않고 local/test Hibernate `ddl-auto=create`를 사용한다.

## 수정 파일 요약

| 구분 | 파일 |
| --- | --- |
| 쿠폰 구현 | `apps/commerce-api/src/main/java/com/loopers/{domain,application,infrastructure,interfaces/api}/coupon/**` |
| 주문 연동 | `application/ordering/order/**`, `domain/ordering/order/Order.java`, `infrastructure/ordering/order/OrderJpaEntity.java`, `interfaces/api/ordering/**` |
| 결제/이벤트 | `domain/payment/payment/PaymentStatus.java`, `domain/event/order/OrderPaidEvent.java` |
| 테스트 | `apps/commerce-api/src/test/java/com/loopers/**` |
| 작업자 문서 | `AGENTS.md`, `.codeguide/loopers-4-week.md`, `.codeguide/transaction-analysis.md`, `.docs/{README,design-review,domain,architecture,dto-spec,test-plan,worklog}.md` |
| 제출 문서 | `.docs/design/*` 수정 없음 |
| 제출 제외 | `AGENTS.md`, `.codeguide/**`, `.docs/**`, `workflow.md`, `modules:redis`, `modules:kafka`, `commerce-streamer` |

## 구현 요약

- 고객 쿠폰 발급, 내 쿠폰 목록과 ADMIN 템플릿 CRUD, 발급 이력 조회 API를 추가했다.
- 발급 쿠폰의 `AVAILABLE`, `USED`, 계산형 `EXPIRED` 상태를 구현했다.
- 주문 요청의 nullable `couponId`는 발급 쿠폰 ID를 의미한다.
- 주문 응답과 `ORDER_PAID` 이벤트는 할인 전 금액, 할인 금액, 최종 금액을 반환한다.
- 동일 쿠폰 동시 주문, 사용자별 동시 발급 한도, 좋아요 병렬 증감 테스트를 추가했다.
- `.codeguide/transaction-analysis.md`에 락 획득 순서와 복구 트랜잭션 점검 기준을 기록했다.
- 4주차 필수 설계와 기존 결제/Outbox 확장 설계를 보조 문서에서 분리했다.
- 결제 worker의 DB 트랜잭션 내부 외부 PG 호출을 후속 개선 항목으로 기록했다.

## 검증 결과

| 명령 | 결과 | 메모 |
| --- | --- | --- |
| `git diff --check -- .docs/README.md .docs/design-review.md .docs/domain.md .docs/architecture.md .docs/dto-spec.md .docs/test-plan.md .docs/worklog.md .codeguide/transaction-analysis.md` | 성공 | 문서 패치 공백 오류 없음 |
| `git diff --name-only -- .docs/design` | 성공 | volume-2 제출 이력 4개 파일 수정 없음 |
| `rg -n "4주차 필수\|4주차 핵심\|기존 확장\|PROCESSING\|lease\|발급 쿠폰 ID" ...` | 성공 | 핵심/확장 경계, 쿠폰 식별자, 결제 worker 후속 목표 반영 확인 |

## 환경 메모

- 로컬 Java는 `C:\Users\woodo\.jdks\ms-21.0.9`를 `JAVA_HOME`으로 지정한다.
- Gradle wrapper 실행은 sandbox 네트워크 제약 때문에 escalated 실행이 필요하다.
- 현재 shell에서는 Docker daemon을 찾지 못하고 외부 MySQL `localhost:3306`도 열려 있지 않다.
- 이전 DB 통합 테스트는 `localhost:3306` MySQL 연결 거부로 실패했다. Docker/Testcontainers도 로컬 daemon 부재로 실행할 수 없었다.

## 다음 작업

1. Docker 또는 MySQL을 사용할 수 있는 환경에서 `:apps:commerce-api:test` 전체를 실행한다.
2. 실제 DB 기반 쿠폰 락, 재고 롤백, E2E 테스트 결과를 확인한다.
3. 0원 주문의 payment row 저장 정책을 확정한다.
4. 결제 worker를 `PROCESSING + lease` 선점, 트랜잭션 밖 PG 호출, 별도 결과 반영 트랜잭션 구조로 개선한다.
