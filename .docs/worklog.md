# Worklog Snapshot

이 문서는 현재 3주차 구현 작업을 이어가기 위한 최신 스냅샷이다.
누적 로그가 아니라 현재 상태만 유지한다.

## 현재 상태

| 항목 | 내용 |
| --- | --- |
| 날짜 | 2026-05-27 |
| 브랜치 | `volume-3` |
| 현재 단계 | 5계층 우선 패키지 구조 복구 및 example 템플릿 제거 완료 |
| 구현 범위 | `apps/commerce-api`는 이번 주차 RDB-only |
| 제외 범위 | Redis, Kafka, cache, message broker 연동 제외 |
| 모듈 경계 | `catalog`, `ordering`, `payment`, `event` |
| 패키지 구조 | `interfaces`, `application`, `domain`, `infrastructure`, `support` 5계층 하위에 도메인 경계를 둠 |
| 지속성 기준 | MySQL/JPA 기반 RDB 저장소와 outbox |

## 최근 결정

- 사용자의 의도에 맞춰 이번 3주차 `commerce-api` 구현은 RDB-only로 정리한다.
- Redis/Kafka는 이번 주차 구현, 테스트, 런타임 의존성에서 사용하지 않는다.
- 기존 저장소에 있는 `modules:redis`, `modules:kafka`, `commerce-streamer`는 별도 영역이므로 삭제하지 않는다.
- `commerce-api`의 Redis 의존성과 `redis.yml` import는 제거했다.
- 이벤트 전달은 Kafka가 아니라 RDB outbox와 worker의 상태 전이로만 표현한다.
- `workflow.md`를 추가해 3주차 RDB-only 흐름과 검증 명령을 시각화했다.
- 저장 처리 고도화는 `AGENTS.md`의 협의 규칙에 따라 질문 1개씩 답변을 받은 뒤 진행한다.
- 저장 처리 관련 코드 변경 전 의도, 선택지, 영향 범위, 검증 방법을 먼저 설명한다.
- 주문 생성 시 재고 차감 동시성 제어는 비관적 락 유지로 확정했다.
- 재고 차감 대상 상품 ID는 정렬 후 조회하고, JPA `PESSIMISTIC_WRITE` 쿼리도 `order by p.id asc`로 고정해 잠금 순서를 일관되게 둔다.
- 사용자 확인에 따라 최상위 패키지는 기존 5계층을 유지하고, `catalog`, `ordering`, `payment`, `event`는 각 계층 하위 도메인 패키지로 둔다.
- 3주차 구현 패키지를 `com.loopers.catalog.*` 형태에서 `com.loopers.domain.catalog.*`, `com.loopers.application.catalog.*` 형태로 정리했다.
- 3주차 실제 도메인 구현이 자리 잡았으므로 템플릿 `example` API, domain, repository, facade, 관련 테스트를 제거했다.

## 수정 파일 요약

| 구분 | 파일 |
| --- | --- |
| 구현 | `apps/commerce-api/src/main/java/com/loopers/{application,domain,infrastructure}/**`, `interfaces/api/{catalog,ordering}/**`, `support/**` |
| 설정 | `apps/commerce-api/build.gradle.kts`, `apps/commerce-api/src/main/resources/application.yml` |
| JPA 설정 | `modules/jpa/src/main/java/com/loopers/config/jpa/JpaConfig.java`, `modules/jpa/src/testFixtures/**` |
| 테스트 | `apps/commerce-api/src/test/java/com/loopers/{application,domain,infrastructure,interfaces}/**` |
| 작업자 문서 | `AGENTS.md`, `workflow.md`, `.codeguide/loopers-3-week.md`, `.docs/README.md`, `.docs/design-review.md`, `.docs/architecture.md`, `.docs/domain.md`, `.docs/worklog.md` |
| 제출 제외 | `AGENTS.md`, `.codeguide/**`, `.docs/**`, `workflow.md`, `modules:redis`, `modules:kafka`, `commerce-streamer` |

## 구현 요약

- 공개 상품 API DTO를 목록/상세로 분리하고 상세 응답에 `stockQuantity`, 브랜드 요약을 포함했다.
- 관리자 상품 생성/수정에서 optional `ProductStatus`를 받을 수 있게 했다.
- `ON_SALE` 상품은 재고가 1개 이상일 때만 허용하고, `STOPPED`는 판매 중지로 처리한다.
- 주문 생성은 상품/재고 검증, 재고 차감, 주문/주문 라인/결제 요청 생성을 하나의 RDB 흐름으로 처리한다.
- 결제 성공은 주문 `PAID` 전이와 주문 완료 outbox 저장을 같은 트랜잭션에서 처리한다.
- 결제 실패/취소/timeout은 주문 실패 전이와 재고 복구를 처리한다.
- 결제 worker와 event relay worker는 RDB pessimistic lock 기반 batch 조회를 사용한다.
- 주문 생성 재고 차감은 RDB pessimistic lock 기반으로 확정했고, 동일 상품 동시 주문 시 재고보다 많은 주문이 생성되지 않는 통합 테스트를 추가했다.
- `ErrorType`은 Spring `HttpStatus` 직접 의존을 제거하고 API advice에서 HTTP 응답으로 매핑한다.
- `PageResponse`에는 `hasPrevious`, `isFirst`, `isLast`를 추가했다.
- 3주차 신규 구현 패키지는 최상위 도메인 우선 구조에서 5계층 우선 구조로 이동했다.
- JPA repository scan은 새 구조의 공통 상위인 `com.loopers.infrastructure`만 사용하도록 정리했다.
- `/api/v1/examples` 템플릿 API와 JPA `example` 엔티티는 제거했다.

## RDB-only 정리

- `apps/commerce-api/build.gradle.kts`에서 `implementation(project(":modules:redis"))` 제거.
- `apps/commerce-api/build.gradle.kts`에서 `testImplementation(testFixtures(project(":modules:redis")))` 제거.
- `apps/commerce-api/src/main/resources/application.yml`에서 `redis.yml` import 제거.
- `apps/commerce-api` 기준 `redis`, `kafka`, `RedisTemplate`, `KafkaTemplate` 문자열 검색 결과 0건.
- Gradle runtimeClasspath 기준 `spring-data-redis`, `spring-kafka` 의존성 없음.
- 새 3주차 도메인 패키지(`domain.catalog`, `domain.ordering`, `domain.payment`, `domain.event`, `support/domain`)는 Spring/JPA/HTTP 타입 직접 의존이 없다.
- 기존 템플릿 `example` 패키지와 테스트는 제거했다.

## 검증 결과

| 명령 | 결과 | 메모 |
| --- | --- | --- |
| `$env:JAVA_HOME='C:\Users\woodo\.jdks\ms-21.0.9'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; .\gradlew.bat --no-daemon :apps:commerce-api:compileJava :apps:commerce-api:compileTestJava` | 성공 | 패키지 이동 후 main/test 컴파일 통과 |
| `$env:JAVA_HOME='C:\Users\woodo\.jdks\ms-21.0.9'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; .\gradlew.bat --no-daemon :apps:commerce-api:test --tests "com.loopers.domain.catalog.*" --tests "com.loopers.domain.ordering.*" --tests "com.loopers.application.catalog.*"` | 성공 | example 제거 후 순수 도메인/카탈로그 애플리케이션 테스트 통과 |
| `docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"` | 실패 | Docker config 접근 거부 및 `docker_engine` pipe 없음. MySQL 상태 확인 불가 |

## 환경 메모

- 로컬 Java는 `C:\Users\woodo\.jdks\ms-21.0.9`를 `JAVA_HOME`으로 지정해 실행했다.
- Gradle wrapper 실행은 sandbox 네트워크 제약 때문에 escalated 실행이 필요했다.
- Java Testcontainers의 Windows npipe Docker API `info` 호출 timeout이 있어, 검증은 `LOOPERS_TESTCONTAINERS_ENABLED=false`와 외부 MySQL datasource 환경변수로 진행했다.
- 외부 MySQL 검증 시 `DATASOURCE_MYSQL_JPA_MAIN_USERNAME=application`, `DATASOURCE_MYSQL_JPA_MAIN_PASSWORD=application`을 함께 지정해야 한다. 누락하면 `${MYSQL_USER}` placeholder가 남아 MySQL 인증에 실패한다.
- 현재 shell에서는 Docker daemon pipe를 찾지 못해 MySQL 컨테이너 상태를 확인하지 못했다.

## 다음 작업

1. Docker/MySQL을 사용할 수 있는 상태에서 `:apps:commerce-api:test`를 다시 실행한다.
2. 저장 처리 고도화 2번 질문을 사용자와 Q&A로 확정한다.
3. 필요하면 기존 `product` legacy/template 패키지를 3주차 아키텍처 기준에 맞게 별도 정리한다.
