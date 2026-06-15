# Worklog Snapshot

이 문서는 현재 5주차 구현 작업을 이어가기 위한 최신 스냅샷이다.
누적 로그가 아니라 현재 상태만 유지한다.

## 현재 상태

| 항목 | 내용 |
| --- | --- |
| 날짜 | 2026-06-16 |
| 브랜치 | `volume-5` |
| 현재 단계 | 5주차 조회 성능 개선 구현 및 E2E 검증 완료, 실제 MySQL `EXPLAIN ANALYZE` 결과 수집 대기 |
| 구현 범위 | `apps/commerce-api` 상품 목록/상세 조회 인덱스, `like_count` 정렬, Redis 캐시, 캐시 무효화, 카탈로그 E2E |
| 제외 범위 | Materialized View, 운영 Flyway migration, 실제 운영 DB 성능 측정 |
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

## 수정 파일 요약

| 구분 | 파일 |
| --- | --- |
| 쿠폰 구현 | `apps/commerce-api/src/main/java/com/loopers/{domain,application,infrastructure,interfaces/api}/coupon/**` |
| 주문 연동 | `application/ordering/order/**`, `domain/ordering/order/Order.java`, `infrastructure/ordering/order/OrderJpaEntity.java`, `interfaces/api/ordering/**` |
| 결제/이벤트 | `domain/payment/payment/PaymentStatus.java`, `domain/event/order/OrderPaidEvent.java` |
| 테스트 | `apps/commerce-api/src/test/java/com/loopers/**` |
| 5주차 캐시/인덱스 | `application/catalog/product/ProductCacheRepository.java`, `infrastructure/catalog/product/{RedisProductCacheRepository,ProductCacheProperties}.java`, `ProductJpaEntity.java`, `ProductRepositoryImpl.java` |
| 5주차 무효화 | `application/catalog/{product,like,brand}/**Service.java` |
| 작업자 문서 | `AGENTS.md`, `.codeguide/loopers-4-week.md`, `.codeguide/loopers-5-week.md`, `.codeguide/loopers-5-week-performance.{md,sql}`, `.codeguide/transaction-analysis.md`, `.docs/{README,design-review,domain,architecture,dto-spec,test-plan,worklog}.md` |
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

## 검증 결과

| 명령 | 결과 | 메모 |
| --- | --- | --- |
| `.\gradlew.bat :apps:commerce-api:test --tests com.loopers.interfaces.api.catalog.CatalogApiE2ETest` | 성공 | Docker compose MySQL/Redis, `LOOPERS_TESTCONTAINERS_ENABLED=false` 환경에서 카탈로그 E2E 통과 |
| `.\gradlew.bat :apps:commerce-api:test` | 실패 후 수정 | 기존 테스트 전용 `CommerceApiTestApplication`과 실제 앱의 `@SpringBootConfiguration` 중복 탐색으로 7개 초기화 실패 |
| `.\gradlew.bat :apps:commerce-api:test` | 성공 | `CommerceApiTestApplication`을 `@TestConfiguration`으로 조정 후 116개 테스트 통과 |

## 환경 메모

- 로컬 Java는 `C:\Users\woodo\.jdks\ms-21.0.9`를 `JAVA_HOME`으로 지정한다.
- Gradle wrapper 실행은 sandbox 네트워크 제약 때문에 escalated 실행이 필요하다.
- Docker compose `mysql`, `redis-master`, `redis-readonly` 컨테이너를 사용해 E2E와 전체 테스트를 검증했다.
- `ErrorType.java` 파일 내용의 conflict marker는 제거했지만, Git index는 아직 `UU`로 표시된다. 커밋 전 `git add apps/commerce-api/src/main/java/com/loopers/support/error/ErrorType.java`로 resolved 처리 필요.

## 다음 작업

1. `.codeguide/loopers-5-week-performance.sql`을 실행해 AS-IS/TO-BE `EXPLAIN ANALYZE` 결과와 응답 시간을 기록한다.
2. 커밋 전 `ErrorType.java`와 `AGENTS.md`의 Git conflict index 상태를 resolved 처리한다.
3. 실제 운영 migration 방식이 필요하면 Flyway 또는 운영 DDL 반영 정책을 별도 합의한다.
