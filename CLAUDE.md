# CLAUDE.md

이 파일은 이 저장소에서 작업하는 Claude Code (claude.ai/code) 에게 가이드를 제공합니다.

## Stack & Tooling

- Java 21 (Gradle toolchain), Spring Boot 3.4.4, Spring Cloud 2024.0.1
- 빌드: Gradle (Kotlin DSL), 루트 `settings.gradle.kts` 를 통한 멀티 모듈 구성. 그룹: `com.loopers`. 프로젝트 버전은 짧은 git hash 로 자동 설정됨 (`build.gradle.kts:5`).
- 영속성: Spring Data JPA + QueryDSL (jakarta classifier), MySQL 8 (Hibernate `ddl-auto=create` 는 `local`/`test` 프로필에서만 동작).
- 캐시: Redis 7 master/replica 구성 (lettuce). 기본 템플릿은 `READ_FROM=REPLICA_PREFERRED`, 명시적 master qualifier 는 `MASTER` 사용.
- 메시징: Kafka 3.5 (KRaft) — 공용 `BATCH_LISTENER` 팩토리는 `modules/kafka` 참고.
- 테스트: JUnit 5, Mockito, springmockk, Instancio, Testcontainers (MySQL/Redis).

## Common Commands

저장소 루트에서 Gradle wrapper 로 실행합니다.

```powershell
# 로컬 인프라 기동 (MySQL, Redis master+replica, Kafka, Kafka UI)
docker-compose -f .\docker\infra-compose.yml up
# 선택: Prometheus + Grafana (http://localhost:3000, admin/admin)
docker-compose -f .\docker\monitoring-compose.yml up

# 전체 빌드 (테스트 제외)
.\gradlew build -x test

# 모든 모듈 테스트 실행
.\gradlew test

# 특정 모듈만 테스트
.\gradlew :apps:commerce-api:test
.\gradlew :modules:jpa:test

# 단일 테스트 클래스 / 메서드 실행
.\gradlew :apps:commerce-api:test --tests com.loopers.domain.example.ExampleModelTest
.\gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ExampleV1ApiE2ETest.Get.*"

# 로컬에서 앱 실행 (기본 프로필 `local`)
.\gradlew :apps:commerce-api:bootRun
.\gradlew :apps:commerce-streamer:bootRun

# 배치 잡 실행 (commerce-batch 는 `job.name` 시스템 프로퍼티로 잡을 선택)
.\gradlew :apps:commerce-batch:bootRun --args="--job.name=demoJob"

# JaCoCo 리포트 (모듈별, `test` 이후 실행)
.\gradlew :apps:commerce-api:jacocoTestReport
```

테스트 JVM 설정은 `build.gradle.kts:80` 에 고정되어 있습니다: `Asia/Seoul` 타임존, `spring.profiles.active=test`, `maxParallelForks=1`. 테스트가 이 값을 가정하므로 특별한 이유 없이 변경하지 마세요.

`commerce-api` Swagger UI: `http://localhost:8080/swagger-ui.html` (`application.yml` 에 따라 `prd`/`qa` 에서는 비활성).

## Multi-Module Layout

루트 빌드 (`build.gradle.kts:36`) 는 모든 서브 프로젝트에 `java`, `org.springframework.boot`, `io.spring.dependency-management`, `jacoco` 를 적용하고, 컨테이너 역할을 하는 세 프로젝트 (`apps`, `modules`, `supports`) 의 task 는 비활성화하여 leaf 모듈만 빌드되도록 합니다.

`build.gradle.kts:75` 의 핵심 규칙: **부모가 `apps` 인 모듈만** `bootJar` 를 생성하고, 나머지는 모두 일반 `jar` 를 생성합니다. 라이브러리 모듈에 Spring Boot 실행 패키징을 추가하지 마세요.

```
apps/        실행 가능한 Spring Boot 앱 (bootJar)
  commerce-api        REST API (servlet, springdoc), jpa+redis+supports 의존
  commerce-batch      Spring Batch 실행기 (web-application-type: none), `spring.batch.job.name` 으로 프로세스 당 1 잡
  commerce-streamer   Kafka 컨슈머 (servlet), jpa+redis+kafka+supports 의존
modules/     재사용 가능한 Spring 설정 (java-library)
  jpa                 Datasource/JPA/QueryDSL 설정 + `BaseEntity` + jpa.yml; testFixtures 제공 (Testcontainers + DatabaseCleanUp)
  redis               Master/replica Lettuce 설정; testFixtures = Testcontainers + RedisCleanUp
  kafka               공용 producer/consumer/listener-factory 빈
supports/    횡단 관심 add-on (java-library)
  jackson, logging, monitoring   (logging/monitoring 은 설정/리소스 전용 — XML appender + yml)
```

각 앱의 `application.yml` 은 `spring.config.import` 로 모듈 설정을 가져옵니다 (예: `jpa.yml`, `redis.yml`, `logging.yml`, `monitoring.yml`, `kafka.yml`). 새 모듈 설정을 추가하려면 모듈의 `src/main/resources` 에 `<name>.yml` 을 두고 사용하는 앱의 `import` 항목에 추가하세요.

## App Architecture (commerce-api)

`com.loopers` 하위에 레이어별로 패키지를 둡니다:

- `interfaces/api/<feature>` — REST 컨트롤러 + `*V1Dto` 요청/응답 record. 컨트롤러는 얇게 유지: 파싱 → facade 호출 → `ApiResponse` 로 래핑.
- `application/<feature>` — `*Facade` (오케스트레이션) + `*Info` DTO. 컨트롤러는 오직 facade 만 호출하며, facade 는 도메인 모델을 `Info` 로 변환합니다.
- `domain/<feature>` — `*Model` (JPA 엔티티, 생성자/변경 메서드에서 invariant 검증), `*Service` (트랜잭션 비즈니스 로직), `*Repository` (인터페이스, Spring Data 사용 금지).
- `infrastructure/<feature>` — `*JpaRepository` (Spring Data) + 도메인 `*Repository` 포트를 구현하는 `*RepositoryImpl`. 새 애그리거트의 영속성을 추가하려면 두 파일을 함께 작성합니다.
- `support/error` — `CoreException` + `ErrorType` enum. `CoreException(ErrorType.X, "msg")` 를 throw 하면 `ApiControllerAdvice` 가 일관된 `ApiResponse.fail(...)` 응답으로 매핑합니다.

모든 HTTP 응답은 `ApiResponse<T>` (record: `Metadata{result, errorCode, message}` + `data`) 로 통일됩니다. `ApiControllerAdvice` 는 이미 `CoreException`, `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, `HttpMessageNotReadableException` (enum/누락 필드에 대한 친절한 메시지 포함), `ServerWebInputException`, `NoResourceFoundException`, 그리고 `Throwable` fallback 까지 처리하므로, 컨트롤러에서 직접 catch 하지 말고 이 클래스를 확장하세요.

엔티티는 `modules/jpa` 의 `BaseEntity` (`modules/jpa/src/main/java/com/loopers/domain/BaseEntity.java`) 를 상속합니다: IDENTITY id, `createdAt`/`updatedAt`/`deletedAt` (soft delete), 그리고 `@PrePersist`/`@PreUpdate` 시점에 호출되는 cross-field invariant 용 `protected guard()` 훅 제공. `delete()` / `restore()` 는 멱등합니다.

## Test Conventions

- 테스트는 Testcontainers (MySQL/Redis) 를 사용 — Docker 가 실행 중이어야 합니다. `test` 프로필도 `ddl-auto=create` 를 유지합니다.
- E2E 테스트는 `*ApiE2ETest` 패턴을 따릅니다: `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `TestRestTemplate` 주입, `@AfterEach` 에서 `databaseCleanUp.truncateAllTables()` 호출. `DatabaseCleanUp` (`modules/jpa` testFixtures) 은 JPA 메타모델을 조회해 `@Entity @Table` 모든 테이블을 `FOREIGN_KEY_CHECKS=0` 상태로 truncate 합니다.
- 앱은 `testImplementation(testFixtures(project(":modules:jpa")))` 형태로 testFixtures 를 소비합니다. cleanup 헬퍼나 Testcontainers 설정이 필요한 새 앱 모듈도 동일하게 추가하세요.
- 기존 테스트 네이밍: `*ModelTest` (단위, Spring 미사용), `*ServiceIntegrationTest`, `*ApiE2ETest`. DisplayName 은 한국어이며 HTTP 엔드포인트별로 `@Nested` 를 사용합니다.

## Local Profiles

프로필은 `spring.profiles.active` 로 활성화합니다: `local` (`bootRun` 기본값), `test` (Gradle test task 가 강제), `dev`, `qa`, `prd`. 로컬 DB 자격증명은 `application/application`, 접속 URL 은 `jdbc:mysql://localhost:3306/loopers`. `prd` 프로필은 springdoc API 문서를 비활성화합니다.