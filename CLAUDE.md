# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트 개요

Loopers 백엔드 학습용 멀티 모듈 프로젝트. Spring Boot 3.4.4 + Java 21 (toolchain) + Gradle (Kotlin DSL).
Group: `com.loopers`, 모든 코드는 이 패키지 밑으로 들어간다.

## 빌드 / 실행 / 테스트

Windows PowerShell 기준 (`gradlew` 도 동일하게 동작).

```powershell
# 전체 빌드 (모든 서브 프로젝트)
./gradlew.bat build

# 특정 앱만 빌드
./gradlew.bat :apps:commerce-api:build

# 실행 가능한 BootJar 만들기 (apps/* 만 BootJar 활성, modules/supports 는 plain Jar)
./gradlew.bat :apps:commerce-api:bootJar

# 로컬 실행 (인프라 먼저 띄울 것 — 아래 도커 섹션 참고)
./gradlew.bat :apps:commerce-api:bootRun
./gradlew.bat :apps:commerce-streamer:bootRun
./gradlew.bat :apps:commerce-batch:bootRun --args='--job.name=demoJob'   # batch 는 job 이름 인자 필수

# 전체 테스트
./gradlew.bat test

# 특정 모듈/앱 테스트
./gradlew.bat :apps:commerce-api:test

# 단일 클래스 / 단일 메서드
./gradlew.bat :apps:commerce-api:test --tests "com.loopers.domain.example.ExampleServiceIntegrationTest"
./gradlew.bat :apps:commerce-api:test --tests "*ExampleV1ApiE2ETest.Get.returnsExampleInfo*"

# Jacoco 커버리지 (test 이후 실행)
./gradlew.bat :apps:commerce-api:jacocoTestReport
```

테스트 공통 설정 (`build.gradle.kts` `subprojects { tasks.test {...} }`):
- `spring.profiles.active=test` 가 강제 주입된다.
- `user.timezone=Asia/Seoul`.
- `maxParallelForks=1` (병렬 실행 안 함 — Testcontainers/공유 DB 충돌 방지).
- 루트 컨테이너 프로젝트(`apps`, `modules`, `supports`)는 `tasks.configureEach { enabled = false }` 라서 직접 task 가 돌지 않는다.

## 로컬 인프라 (Docker)

테스트는 Testcontainers 가 알아서 띄우지만, **`bootRun` 으로 로컬 실행할 때는 반드시 docker-compose 가 먼저 떠 있어야 한다.**

```powershell
docker-compose -f ./docker/infra-compose.yml up        # mysql, redis-master, redis-readonly, kafka, kafka-ui
docker-compose -f ./docker/monitoring-compose.yml up   # prometheus, grafana (http://localhost:3000, admin/admin)
```

포트:
- MySQL `3306` (db `loopers`, user `application`/`application`)
- Redis master `6379`, readonly replica `6380`
- Kafka broker `19092` (host listener), kafka-ui `9099`
- App actuator/management `8081` (별도 포트), Swagger UI `commerce-api: /swagger-ui.html`

## 모듈 구조와 의존 규칙

```
apps/        # 실행 가능한 SpringBootApplication (BootJar)
  commerce-api       # REST API (Web MVC)
  commerce-streamer  # Kafka Consumer (Web MVC + Kafka)
  commerce-batch     # Spring Batch (web-application-type=none)
modules/     # 도메인 무관 + 재사용 가능한 configuration (`java-library` + `java-test-fixtures`)
  jpa, redis, kafka  # 각자 testFixtures 에 Testcontainers config 제공
supports/    # logging/monitoring/jackson 같은 add-on (도메인 무관)
```

규칙:
- `apps/*` 만 BootJar, `modules/*` `supports/*` 는 plain Jar (`build.gradle.kts` 80~ 라인의 task 분기).
- 새 앱은 항상 필요한 modules/supports 를 `implementation(project(":modules:..."))` 로 명시. 예: `commerce-api/build.gradle.kts` 참고.
- modules 의 testFixtures (`testImplementation(testFixtures(project(":modules:jpa")))`) 가 `DatabaseCleanUp`, `MySqlTestContainersConfig` 등을 제공한다 — 통합/E2E 테스트에서 반드시 가져다 써야 한다.

## commerce-api 아키텍처 (레이어드)

```
interfaces.api.<domain>      # @RestController + V1Dto (record). API 진입점만 담당.
   ↓
application.<domain>         # XxxFacade (@Component) + XxxInfo (record DTO). 유스케이스 조립 + 변환 책임.
   ↓
domain.<domain>              # XxxModel (Entity) + XxxService + XxxRepository (인터페이스). 비즈니스 규칙.
   ↓
infrastructure.<domain>      # XxxRepositoryImpl + XxxJpaRepository (Spring Data). 외부 의존(JPA) 격리.
support.error                # CoreException + ErrorType (HttpStatus / code / message 매핑)
```

흐름의 핵심 규칙:
- **도메인은 인프라를 모른다.** `domain.XxxRepository` 는 인터페이스, `infrastructure.XxxRepositoryImpl` 가 `XxxJpaRepository` 를 위임 호출하며 인터페이스를 구현한다.
- **Controller 는 Facade 만 부른다.** Service 직접 호출 금지. Facade 가 여러 Service 를 조립하거나 `XxxModel → XxxInfo` 변환을 담당.
- **Service 가 트랜잭션 경계** (`@Transactional` / `@Transactional(readOnly = true)`).
- 도메인 검증 실패는 `throw new CoreException(ErrorType.BAD_REQUEST, "...")` — `ApiControllerAdvice` 가 받아서 표준 응답으로 직렬화한다 (`ProductModel` 생성자 참고).
- **모든 응답은 `ApiResponse<T>` 로 감싼다.** `meta(result, errorCode, message)` + `data` 구조. 컨트롤러는 `ApiResponse.success(data)` 형태로 리턴.

새 도메인 추가 시 `example` / `product` 패키지를 그대로 베껴서 5개 파일 (Model / Repository(interface) / Service / RepositoryImpl / JpaRepository) + Facade + Info + V1Controller + V1Dto 를 만든다.

## 엔티티 / DB 컨벤션

`modules/jpa` 의 `BaseEntity` 를 **반드시** 상속:
- `id` (IDENTITY), `createdAt`/`updatedAt`/`deletedAt` (`ZonedDateTime`) 자동 관리.
- `@PrePersist`/`@PreUpdate` 에서 `guard()` 호출 — 엔티티 추가 검증을 넣고 싶으면 `guard()` 를 override.
- `delete()`/`restore()` 는 멱등 (soft delete 전제).

JPA 설정 (`modules/jpa/src/main/resources/jpa.yml`):
- 기본 `ddl-auto: none`. **단, profile `local`/`test` 는 `create`** (테스트 사이 schema 가 매번 새로 만들어진다).
- `open-in-view: false`, UTC 저장 / 정규화.
- HikariCP (`mySqlMainHikariConfig`) 가 `datasource.mysql-jpa.main.*` 를 읽음. 새 데이터소스를 추가할 때도 이 컨벤션을 유지.

## 테스트 컨벤션

- **단위 테스트**: `XxxModelTest`, `CoreExceptionTest` 같은 POJO 테스트.
- **통합 테스트**: `@SpringBootTest` + `@AfterEach databaseCleanUp.truncateAllTables()`. `DatabaseCleanUp` 은 `JpaRepository` 와 함께 `testFixtures(":modules:jpa")` 에서 주입.
- **E2E 테스트**: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` + `ParameterizedTypeReference<ApiResponse<...>>`.
- DisplayName 은 한글, `@Nested` 로 동작 그룹핑 (`Get`, `Create` 등). 메서드명은 영문 `verb_when조건` 패턴.
- Testcontainers MySQL 은 `MySqlTestContainersConfig` static 블록에서 한 번만 시작되고 `System.setProperty` 로 datasource URL 을 주입한다 — 별도 `@Testcontainers` 어노테이션 불필요.

## 환경 / 프로파일

- 모든 `application.yml` 이 `spring.profiles.active=local` 기본값. `local`/`test`/`dev`/`qa`/`prd` 가 multi-document 로 정의됨.
- `commerce-api/application.yml` 이 `jpa.yml`, `redis.yml`, `logging.yml`, `monitoring.yml` 을 `spring.config.import` 로 합친다. `commerce-streamer` 는 추가로 `kafka.yml` 을 import.
- prd/qa 환경은 환경 변수 (`MYSQL_HOST`, `MYSQL_USER`, `REDIS_MASTER_HOST`, `BOOTSTRAP_SERVERS` 등) 로 주입.
- `CommerceApiApplication` 의 `@PostConstruct` 가 JVM 기본 타임존을 `Asia/Seoul` 로 강제.

## commerce-streamer / commerce-batch 메모

- streamer: `KafkaConfig.BATCH_LISTENER` 컨테이너 팩토리를 사용한 batch listener (concurrency 3, 수동 ack, max-poll 3000).
- batch: `@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = JOB_NAME)` 으로 job 단위로 활성화. 실행 시 `--job.name=demoJob` 인자 필수, 미지정 시 `NONE` 으로 떠서 아무 job 도 실행되지 않음.

## 코드 스타일

- `.editorconfig`: max line 130 (`*Test.java` 는 무제한), 끝 newline 필수.
- Lombok 사용 (`@RequiredArgsConstructor`, `@Getter`, `@Slf4j`). 생성자 주입은 Lombok 으로 처리.
- DTO/Info/Response 는 거의 모두 `record`.
- 사용자 메시지/주석은 한글, 클래스/메서드명은 영문.

## 개발 규칙
### 진행 Workflow - 증강 코딩
- **대원칙** : 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행.
- **중간 결과 보고** : AI 가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입.
- **설계 주도권 유지** : AI 가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행.

### 개발 Workflow - TDD (Red > Green > Refactor)
- 모든 테스트는 3A 원칙으로 작성할 것 (Arrange - Act - Assert)
#### 1. Red Phase : 실패하는 테스트 먼저 작성
- 요구사항을 만족하는 기능 테스트 케이스 작성
- 테스트 예시
#### 2. Green Phase : 테스트를 통과하는 코드 작성
- Red Phase 의 테스트가 모두 통과할 수 있는 코드 작성
- 오버엔지니어링 금지
#### 3. Refactor Phase : 불필요한 코드 제거 및 품질 개선
- 불필요한 private 함수 지양, 객체지향적 코드 작성
- unused import 제거
- 성능 최적화
- 모든 테스트 케이스가 통과해야 함

## 주의사항
### 1. Never Do
- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이요한 구현을 하지 말 것
- null-safety 하지 않게 코드 작성하지 말 것 (Java 의 경우, Optional 을 활용할 것)
- println 코드 남기지 말 것

### 2. Recommendation
- 실제 API 를 호출해 확인하는 E2E 테스트 코드 작성
- 재사용 가능한 객체 설계
- 성능 최적화에 대한 대안 및 제안
- 개발 완료된 API 의 경우, `.http/**.http` 에 분류해 작성

### 3. Priority
1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지