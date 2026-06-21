# Worklog Snapshot

이 문서는 현재 구현 및 문서 작업을 이어가기 위한 최신 스냅샷이다.
누적 로그가 아니라 현재 상태만 유지한다.

## 현재 상태

| 항목 | 내용 |
| --- | --- |
| 날짜 | 2026-06-21 |
| 브랜치 | `volume-6` |
| 현재 단계 | 6주차 PG simulator 결제 요청/콜백/수동 동기화 구현 및 결제 API E2E 검증 완료 |
| 구현 범위 | `apps/commerce-api` 결제 API, PG simulator client, Resilience guard, `PaymentStatus.PROCESSING` |
| 제외 범위 | Retryer, lease 만료시각, 운영 Flyway migration, 실제 `apps:pg-simulator` 기동 기반 수동 E2E |
| 모듈 경계 | `catalog`, `coupon`, `ordering`, `payment`, `event` |
| 패키지 구조 | `interfaces`, `application`, `domain`, `infrastructure`, `support` 5계층 하위에 도메인 경계를 둠 |
| 지속성 기준 | MySQL/JPA 기준 유지, 상품 조회 캐시는 Redis string cache 사용 |

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
- 4주차 트랜잭션 분석은 `.codex/skills/loopers-transaction-analysis` Codex 스킬로도 수행한다.
- 5주차 가이드는 조회 성능 개선을 DB Index, 비정규화, Redis 캐시, 분석/기록 기준으로 나누어 관리한다.
- 상품 좋아요 순 정렬은 `product.like_count` 비정규화를 사용하고, 좋아요 등록/취소 트랜잭션에서 동기화한다.
- 공개 상품 목록 캐시 key는 `status`, `brandId`, `sort`, `page`, `size`를 포함한다.
- 상품 상세 캐시는 상품 ID 단위로 저장하며 TTL은 `commerce.cache.product.ttl=5m`로 둔다.
- 사용자별 `liked` 값은 Redis에 저장하지 않고 응답 직전에 `product_like` 기준으로 다시 계산한다.
- 상품/브랜드/좋아요 쓰기 후 상품 목록 캐시와 관련 상품 상세 캐시를 무효화한다.
- 5주차 E2E는 Docker compose MySQL/Redis를 사용하고, `LOOPERS_TESTCONTAINERS_ENABLED=false`로 Redis Testcontainers 자동 기동을 끈다.
- 테스트 프로파일의 기본 MySQL URL은 compose DB인 `jdbc:mysql://localhost:3306/loopers`로 둔다. Testcontainers를 켜면 기존 EnvironmentPostProcessor가 URL을 덮어쓴다.
- 6주차 가이드는 PG 결제 연동을 결제 API, PG simulator, 상태 정합성, Resilience, 검증 체크리스트로 재정리했다.
- PG 비동기 결제는 요청 성공과 최종 결제 성공을 분리하고, 콜백 누락 시 결제 상태 조회로 복구하는 기준을 명시했다.
- 6주차 결제 시작은 `POST /api/v1/payments`에서 카드 정보를 받아 PG simulator에 요청한다. 주문 생성은 기존처럼 `PaymentStatus.REQUESTED` row만 만든다.
- PG simulator `PENDING`은 내부 `PaymentStatus.PROCESSING`으로 매핑하고 transactionKey를 저장한다.
- PG에는 내부 Long 주문 ID를 6자리 이상 문자열로 변환해 보낸다. 예: `1 -> "000001"`, `1351039135 -> "1351039135"`.
- 외부 PG 장애, timeout, CircuitBreaker open은 `EXTERNAL_SYSTEM_UNAVAILABLE(503)`과 "일시적으로 결제를 사용할 수 없습니다." 메시지로 응답한다.
- PG 호출 보호는 `@ExternalSystemGuard` 어노테이션과 Resilience4j CircuitBreaker로 공통 처리한다.
- `PaymentFacade`에는 DB 트랜잭션을 걸지 않아 PG 호출을 트랜잭션 밖에 둔다.
- 결제 요청에서 주문 소유권을 확인할 때 lazy loading이 세션 밖에서 터지지 않도록 `OrderJpaRepository.findByIdAndUserId`는 `lines` entity graph를 사용한다.

## 수정 파일 요약

| 구분 | 파일 |
| --- | --- |
| 쿠폰 구현 | `apps/commerce-api/src/main/java/com/loopers/{domain,application,infrastructure,interfaces/api}/coupon/**` |
| 주문 연동 | `application/ordering/order/**`, `domain/ordering/order/Order.java`, `infrastructure/ordering/order/{OrderJpaEntity,OrderJpaRepository}.java`, `interfaces/api/ordering/**` |
| 결제/이벤트 | `application/payment/payment/**`, `domain/payment/{payment,gateway}/**`, `infrastructure/payment/{gateway,payment}/**`, `interfaces/api/payment/**`, `domain/event/order/OrderPaidEvent.java` |
| 테스트 | `apps/commerce-api/src/test/java/com/loopers/**` |
| 5주차 캐시/인덱스 | `application/catalog/product/ProductCacheRepository.java`, `infrastructure/catalog/product/{RedisProductCacheRepository,ProductCacheProperties}.java`, `ProductJpaEntity.java`, `ProductRepositoryImpl.java` |
| 5주차 무효화 | `application/catalog/{product,like,brand}/**Service.java` |
| 작업자 문서 | `AGENTS.md`, `.codeguide/loopers-4-week.md`, `.codeguide/loopers-5-week.md`, `.codeguide/loopers-6-week.md`, `.codeguide/loopers-5-week-performance.{md,sql}`, `.codeguide/transaction-analysis.md`, `.docs/{README,design-review,domain,architecture,dto-spec,test-plan,worklog}.md` |
| 6주차 Resilience | `support/external/{ExternalSystemGuard,ExternalSystemGuardAspect,ExternalSystemGuardConfig}.java`, `support/error/ErrorType.java` |
| HTTP 예시 | `http/commerce-api/payments.http`, `http/pg-simulator/payments.http` |
| Codex 스킬 | `.codex/skills/loopers-transaction-analysis/{SKILL.md,agents/openai.yaml,references/week4-transaction-checklist.md}` |
| 제출 문서 | `.docs/design/*` 수정 없음 |
| 제출 제외 | `AGENTS.md`, `.codeguide/**`, `.docs/**`, `workflow.md`, `modules:redis`, `modules:kafka`, `commerce-streamer` |

## 구현 요약

- 고객 쿠폰 발급, 내 쿠폰 목록과 ADMIN 템플릿 CRUD, 발급 이력 조회 API를 추가했다.
- 발급 쿠폰의 `AVAILABLE`, `USED`, 계산형 `EXPIRED` 상태를 구현했다.
- 주문 요청의 nullable `couponId`는 발급 쿠폰 ID를 의미한다.
- 주문 응답과 `ORDER_PAID` 이벤트는 할인 전 금액, 할인 금액, 최종 금액을 반환한다.
- 동일 쿠폰 동시 주문, 사용자별 동시 발급 한도, 좋아요 병렬 증감 테스트를 추가했다.
- `.codeguide/transaction-analysis.md`에 락 획득 순서와 복구 트랜잭션 점검 기준을 기록했다.
- `.codex/skills/loopers-transaction-analysis`에 한국어 트랜잭션 분석 스킬과 4주차 체크리스트 reference를 추가했다.
- 4주차 필수 설계와 기존 결제/Outbox 확장 설계를 보조 문서에서 분리했다.
- 결제 worker의 DB 트랜잭션 내부 외부 PG 호출을 후속 개선 항목으로 기록했다.
- `.codeguide/loopers-5-week.md`를 목적, 목표, 사전 설계 점검, 구현 과제, 분석 기준, 체크리스트 구조로 재정리했다.
- `commerce-api`가 `modules:redis`를 사용하도록 연결하고 `redis.yml`을 import했다.
- 상품 공개 목록/상세 조회에 Redis cache-aside를 적용했다. Redis 장애 또는 cache miss 시 DB 조회로 fallback한다.
- `product` 테이블에 `status, brand_id, like_count`, `status, like_count`, `status, brand_id, created_at`, `status, brand_id, price` 인덱스를 선언했다.
- 공개 상품 조회는 `brandId` optional `OR` 조건 대신 `brandId` 유무에 따라 derived query로 분기한다.
- `.codeguide/loopers-5-week-performance.sql`에 10만 건 상품 데이터 준비와 `EXPLAIN ANALYZE` 쿼리를 추가했다.
- 카탈로그 E2E에 좋아요순 목록 조회 캐시 생성, 좋아요 변경 후 목록 캐시 무효화, 상품 상세 캐시 무효화와 사용자별 `liked` 재계산 검증을 추가했다.
- 테스트 전용 `CommerceApiTestApplication`은 `@TestConfiguration`으로 바꿔 실제 `CommerceApiApplication`과의 `@SpringBootConfiguration` 탐색 충돌을 제거했다.
- `.codeguide/loopers-6-week.md`를 목적, 목표, 사전 설계 점검, 구현 과제, PG simulator 동작 기준, 분석 기준, 체크리스트 구조로 재정리했다.
- `PaymentStatus.PROCESSING`을 추가해 PG 요청 접수 후 최종 결과 대기 상태를 표현한다.
- `PaymentFacade`를 추가해 결제 요청, PG 콜백, 수동 상태 동기화를 조합한다.
- `PgSimulatorPaymentGateway`는 RestTemplate으로 PG 요청/transactionKey 조회/orderId 조회를 호출하며 connect timeout 300ms, read timeout 800ms를 사용한다.
- 실제 PG 호출 메서드에는 `@ExternalSystemGuard(value = "일시적으로 결제를 사용할 수 없습니다.", name = "pg-simulator")`를 적용했다.
- PG 성공 콜백은 결제 `SUCCESS`, 주문 `PAID`, `ORDER_PAID` outbox 저장으로 반영한다.
- PG 실패 콜백 또는 상태조회 결과는 결제 `FAILED`, 주문 `PAYMENT_FAILED`, 재고/쿠폰 복구로 반영한다.
- `/api/v1/payments/{orderId}/sync`를 수동 복구 API로 추가해 콜백 누락 시 PG 상태 조회를 통해 반영할 수 있게 했다.
- `PaymentFacadeTest`에 PG 주문 ID 6자리 padding, 중복 성공 콜백 멱등성, 콜백 누락 후 transactionKey 상태 조회 복구 검증을 추가했다.
- `PgSimulatorPaymentGatewayTest`로 PG 결제 요청, transactionKey 조회, orderId 조회 응답 매핑을 검증한다.
- `ExternalSystemGuardAspectTest`는 일반 외부 실패, timeout, CircuitBreaker open 상태의 503 fallback을 검증한다.
- `PaymentApiE2ETest`는 compose MySQL/Redis 기반에서 `POST /api/v1/payments`가 내부 결제를 `PROCESSING`으로 바꾸는 흐름을 검증한다.

## 검증 결과

| 명령 | 결과 | 메모 |
| --- | --- | --- |
| `.\gradlew.bat :apps:commerce-api:test --tests com.loopers.application.payment.payment.* --tests com.loopers.infrastructure.payment.scheduler.PaymentWorkerSchedulerTest --tests com.loopers.infrastructure.payment.gateway.PgSimulatorPaymentGatewayTest --tests com.loopers.support.external.ExternalSystemGuardAspectTest --tests com.loopers.interfaces.api.payment.PaymentApiE2ETest` | 성공 | compose MySQL/Redis, `LOOPERS_TESTCONTAINERS_ENABLED=false`; 6주차 결제/API/PG adapter/Resilience 묶음 통과 |
| `.\gradlew.bat :apps:commerce-api:test --tests com.loopers.interfaces.api.payment.PaymentApiE2ETest` | 성공 | compose MySQL/Redis 기반 결제 API E2E 통과 |
| `git diff --check` | 성공 | CRLF 변환 경고만 출력, whitespace 오류 없음 |

## 환경 메모

- 로컬 Java는 `C:\Users\woodo\.jdks\ms-21.0.9`를 `JAVA_HOME`으로 지정한다.
- Gradle wrapper 실행은 sandbox 네트워크 제약 때문에 escalated 실행이 필요하다.
- Docker compose `mysql`, `redis-master`, `redis-readonly` 컨테이너를 사용해 E2E와 6주차 관련 테스트 묶음을 검증했다.
- Testcontainers 직접 실행은 현재 환경에서 `Could not find a valid Docker environment`로 실패했다. compose 기반 실행 시 `LOOPERS_TESTCONTAINERS_ENABLED=false`를 사용한다.
- `AGENTS.md` 파일에는 conflict marker가 남아 있다. 이번 작업은 양쪽 규칙을 모두 보수적으로 적용했다.

## 다음 작업

1. 실제 `apps:pg-simulator`를 8082로 띄운 뒤 `http/commerce-api/payments.http`로 결제 요청, 콜백, 수동 동기화를 확인한다.
2. Retryer를 적용할지 결정한다. 적용한다면 PG 멱등성 키와 중복 결제 방지 기준을 먼저 확정한다.
3. 커밋 전 `AGENTS.md` conflict marker와 5주차 성능 문서 삭제 상태를 정리한다.
