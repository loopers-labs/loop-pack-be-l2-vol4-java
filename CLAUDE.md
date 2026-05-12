# CLAUDE.md

이 문서는 Claude Code가 본 저장소에서 작업할 때 참고하는 프로젝트 가이드입니다.

## 프로젝트 개요

`loopers-java-spring-template` — Loopers 가 제공하는 Spring Boot 기반 멀티 모듈 커머스 템플릿 프로젝트입니다. 커머스 도메인의 API / Batch / Streamer 애플리케이션과, 이들이 공통으로 사용하는 인프라(JPA · Redis · Kafka)·부가 기능(jackson · logging · monitoring) 모듈로 구성되어 있습니다.

- 패키지 베이스: `com.loopers`
- 기본 타임존: `Asia/Seoul` (애플리케이션 부팅 시 `TimeZone.setDefault` + Gradle 테스트 systemProperty)
- 기본 프로필: `local` (apps), 테스트 프로필: `test` (Gradle `tasks.test` 에 강제 주입)

## 주요 기술 스택 및 버전

| 분류 | 기술 | 버전 |
| ---- | ---- | ---- |
| Language | Java (Toolchain) | **21** |
| Build | Gradle Kotlin DSL (multi-module) | wrapper 포함 |
| Framework | Spring Boot | **3.4.4** |
| Framework | Spring Cloud BOM | **2024.0.1** |
| Plugin | `io.spring.dependency-management` | **1.1.7** |
| Persistence | Spring Data JPA / Hibernate | Spring Boot 관리 |
| Persistence | QueryDSL (jakarta) | apt 기반 |
| Persistence | MySQL Connector-J | Spring Boot 관리 |
| Cache | Spring Data Redis | Spring Boot 관리 |
| Messaging | Spring Kafka | Spring Boot 관리 |
| Batch | Spring Batch | Spring Boot 관리 (`commerce-batch`) |
| Docs | springdoc-openapi (webmvc-ui) | **2.7.0** |
| Lombok | `org.projectlombok:lombok` | Spring Boot 관리 |
| JSON | Jackson (jsr310, kotlin module) | Spring Boot 관리 |
| Observability | Micrometer Prometheus / Tracing(Brave) | Spring Boot 관리 |
| Logging | Logback + `logback-slack-appender` | **1.6.1** |
| Test - Core | JUnit 5 (junit-platform-launcher) | Spring Boot 관리 |
| Test - Mock | Mockito | **5.14.0** |
| Test - Mock(Kotlin) | springmockk | **4.0.2** |
| Test - Fixture | Instancio JUnit | **5.0.2** |
| Test - Container | Testcontainers (mysql / kafka / redis) | Spring Boot 관리 |
| Coverage | Jacoco (XML 리포트) | Gradle 기본 |

전체 버전 상수는 `gradle.properties` 에 정의되어 있으며, 플러그인 버전은 `settings.gradle.kts` 의 `pluginManagement.resolutionStrategy` 에서 매핑합니다.

### 인프라 (`docker/infra-compose.yml`)

로컬 개발을 위해 다음 인프라가 docker-compose 로 제공됩니다.

- **MySQL 8.0** (`loopers` DB, `application/application` 계정)
- **Redis 7.0 (master + read-only replica)** — replicaof / `--replica-read-only` 구성
- **Kafka 3.5.1** (Bitnami legacy 이미지, KRaft 모드 단일 노드) + **Kafka UI** (`localhost:9099`)
- **Prometheus + Grafana** — `docker/monitoring-compose.yml` (Grafana `localhost:3000`, admin/admin)

## 모듈 구조

```
Root  (rootProject.name = "loopers-java-spring-template")
├── apps/        ← 실행 가능한 SpringBootApplication (BootJar 활성)
│   ├── commerce-api       : REST API. JPA + Redis + springdoc 사용
│   ├── commerce-batch     : Spring Batch 잡 실행기
│   └── commerce-streamer  : Kafka 컨슈머 + web/actuator
├── modules/     ← 재사용 가능한 인프라 configuration (java-library + java-test-fixtures)
│   ├── jpa     : Spring Data JPA, QueryDSL, MySQL driver, BaseEntity, DatabaseCleanUp, MySqlTestContainersConfig
│   ├── redis   : Spring Data Redis, RedisConfig/Properties, RedisCleanUp, RedisTestContainersConfig
│   └── kafka   : Spring Kafka, spring-kafka-test, Testcontainers Kafka
└── supports/    ← 부가 기능 add-on 모듈
    ├── jackson    : JacksonConfig (jsr310, kotlin module)
    ├── logging    : logback.xml + JSON/Plain/Slack appender + Brave tracing
    └── monitoring : Actuator + Micrometer Prometheus
```

### 빌드/모듈 규약 (build.gradle.kts)

- 모든 서브 프로젝트에 `java`, `spring-boot`, `dependency-management`, `jacoco` 플러그인 일괄 적용.
- 공통 의존성으로 `spring-boot-starter`, Lombok, Jackson jsr310, JUnit/Mockito/springmockk/Instancio, Testcontainers 가 부여됩니다.
- **`apps/*` 만 BootJar 활성**, 그 외 (`modules/*`, `supports/*`) 는 일반 `Jar` 활성 — 라이브러리 형태로 사용.
- `apps`, `modules`, `supports` 의 컨테이너 프로젝트 자체는 `tasks.configureEach { enabled = false }` 로 빌드 비활성.
- 테스트는 `useJUnitPlatform()`, `maxParallelForks=1`, `user.timezone=Asia/Seoul`, `spring.profiles.active=test`, `-Xshare:off` 강제.
- Jacoco 는 `test` 이후 실행 (`mustRunAfter("test")`), XML 리포트만 활성.
- 버전 미지정 시 `git rev-parse --short HEAD` 결과를 버전으로 사용 (`getGitHash()`).

### 모듈 간 의존성 (apps 기준)

- `commerce-api` → `modules:jpa`, `modules:redis`, `supports:jackson`, `supports:logging`, `supports:monitoring`
- `commerce-batch` → `modules:jpa`, `modules:redis`, `supports:*` (+ `spring-boot-starter-batch`, `spring-batch-test`)
- `commerce-streamer` → `modules:jpa`, `modules:redis`, `modules:kafka`, `supports:*` (+ `spring-boot-starter-web`)
- 테스트에서는 각 모듈의 `testFixtures` (DatabaseCleanUp / RedisCleanUp / Testcontainers 설정 등) 를 가져다 씁니다.

## 패키지 레이어링

`com.loopers` 하위는 레이어드 + 도메인 패키지 구조로 정리되어 있으며, **DDD 스타일의 패키지 분리** 와 **Facade 패턴** 을 채택하고 있습니다.

### commerce-api

```
com.loopers
├── CommerceApiApplication                # 부트 엔트리, 타임존 고정
├── interfaces
│   └── api                               # 표현 계층 (Controller, DTO, Swagger ApiSpec)
│       ├── ApiResponse                   # 공통 응답 래퍼
│       ├── ApiControllerAdvice           # 전역 예외 매핑 (@RestControllerAdvice)
│       └── <domain>
│           ├── <Domain>V1Controller      # REST 엔드포인트
│           ├── <Domain>V1Dto             # 요청/응답 DTO (record)
│           └── <Domain>V1ApiSpec         # Swagger 어노테이션 분리 인터페이스 (선택)
├── application
│   └── <domain>
│       ├── <Domain>Facade                # 유스케이스 조립 (Service 호출 + Info 변환)
│       └── <Domain>Info                  # Application 계층 DTO (도메인 → Facade 반환값)
├── domain
│   └── <domain>
│       ├── <Domain>Model                 # Entity (불변 조건 가드, 도메인 행위 메서드)
│       ├── <Domain>Service               # 도메인 서비스 (@Component, @Transactional)
│       └── <Domain>Repository            # 리포지토리 포트 (interface)
├── infrastructure
│   └── <domain>
│       ├── <Domain>JpaRepository         # Spring Data JPA Repository
│       └── <Domain>RepositoryImpl        # <Domain>Repository 구현체 (어댑터)
└── support
    └── error
        ├── CoreException                 # 도메인/애플리케이션 예외
        └── ErrorType                     # HTTP 상태 코드 매핑 enum
```

### commerce-batch

```
com.loopers
├── CommerceBatchApplication
└── batch
    ├── job
    │   └── <jobName>
    │       ├── <JobName>JobConfig        # Job/Step 빈 정의
    │       └── step
    │           └── <JobName>Tasklet      # Tasklet 구현
    └── listener
        ├── JobListener                   # Job 시작/종료 이벤트
        ├── StepMonitorListener           # Step 모니터링
        └── ChunkListener                 # Chunk 단위 이벤트
```

### commerce-streamer

```
com.loopers
├── CommerceStreamerApplication
└── interfaces
    └── consumer
        └── <Topic>KafkaConsumer          # @KafkaListener 컨슈머
```

공통 엔티티는 `modules/jpa` 의 `com.loopers.domain.BaseEntity` 에서 제공되며, `createdAt / updatedAt / deletedAt` 자동 관리 + 멱등한 `delete()` / `restore()` 를 포함합니다.

## 채택된 개발 방법론

코드 구조와 git 이력을 종합하면 아래 방식이 일관되게 적용되어 있습니다.

### 1. DDD-lite (도메인 + 레이어드 아키텍처)
- **`interfaces` / `application` / `domain` / `infrastructure`** 4개 레이어로 책임 분리.
- 의존성 방향은 바깥(interfaces) → 안(domain) 으로만 향하며, `domain.<X>.Repository` 는 **포트(인터페이스)**, `infrastructure.<X>.RepositoryImpl` 는 **어댑터** 입니다 (헥사고날에 가까운 포트-어댑터 구조).
- 도메인 모델은 **생성자/메서드 단계에서 불변 조건을 가드**하고 (`ProductModel`, `ExampleModel` 의 검증 로직 참고) 도메인 위반 시 `CoreException(ErrorType.BAD_REQUEST, ...)` 를 던집니다.
- Application 계층은 **Facade 패턴** 을 사용해 Controller 에 단일 진입점을 제공하고, 도메인 → `Info` DTO 변환 책임을 가집니다.
- API 응답은 `ApiResponse<T>` 로 통일되며 예외는 `ApiControllerAdvice` 에서 `ErrorType` 기반으로 일관 변환됩니다.

### 2. Test-Driven Development (TDD)
- 최근 커밋 히스토리 (`test: UserService 단위 테스트 작성 (Mockito TestDouble)` → 후속 구현/리팩터 → 일부 revert) 에서 보이듯, **테스트를 먼저 작성하고 구현을 잇는 워크플로우**가 적용됩니다.
- 테스트는 계층별로 분리되어 있습니다.
  - **도메인 모델 단위 테스트** (`domain/.../ExampleModelTest`)
  - **서비스 통합 테스트** (`domain/.../ExampleServiceIntegrationTest`, `@SpringBootTest` + DatabaseCleanUp + Testcontainers)
  - **API E2E 테스트** (`interfaces/api/ExampleV1ApiE2ETest`)
  - **컨텍스트 로딩 테스트** (`CommerceApiContextTest`)
- 테스트 더블은 Mockito + (필요 시) springmockk, 픽스처는 Instancio, 외부 의존은 Testcontainers (MySQL/Redis/Kafka) 로 실 인프라를 띄워 검증합니다. → **"DB/브로커는 모킹하지 않고 컨테이너로 실제 의존성을 사용한다"** 는 원칙이 반영되어 있습니다.
- 테스트 간 격리는 `DatabaseCleanUp.truncateAllTables()`, `RedisCleanUp` 등 `testFixtures` 유틸로 보장합니다.

### 3. Multi-Module + Convention 분리
- 인프라 설정(`modules/*`) 과 부가 기능(`supports/*`) 을 **재사용 가능한 라이브러리** 로 분리하고, 실행 가능한 애플리케이션은 `apps/*` 에만 둡니다.
- 각 모듈은 `spring.config.import` (`jpa.yml`, `redis.yml`, `logging.yml`, `monitoring.yml`) 로 설정을 합쳐 사용합니다.

### 4. 운영 친화 설정
- 모든 애플리케이션은 Actuator + Prometheus + Brave Tracing 을 기본 탑재.
- 환경별 (`local / test / dev / qa / prd`) 프로필이 `application.yml` 및 각 모듈 `*.yml` 에 분리 정의.
- 운영 환경(`prd / qa / dev` 등) 에서는 Slack appender 로 로그 통보가 가능하도록 logback 설정이 분리되어 있습니다.

### 5. CI 및 협업
- `.github/workflows/main.yml` 에서 [qodo-ai/pr-agent](https://github.com/qodo-ai/pr-agent) 를 PR 자동 리뷰 봇으로 사용.
- `.editorconfig`, `pull_request_template.md`, `auto_assign.yml` 등 협업 컨벤션이 정비되어 있습니다.
- 커밋 메시지는 **Conventional Commits** (`feat:`, `refactor:`, `test:`, `revert:`, `merge:` 등) 스타일을 따릅니다.

## 자주 쓰는 명령어

```shell
# 인프라 기동 (MySQL / Redis / Kafka / Kafka UI)
docker-compose -f ./docker/infra-compose.yml up

# 모니터링 (Prometheus / Grafana, http://localhost:3000 — admin/admin)
docker-compose -f ./docker/monitoring-compose.yml up

# 빌드 / 테스트 (Gradle Wrapper 사용)
./gradlew build
./gradlew :apps:commerce-api:test
./gradlew :apps:commerce-api:jacocoTestReport

# 특정 앱 실행 (BootJar 활성된 apps/* 만 실행 가능)
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-batch:bootRun
./gradlew :apps:commerce-streamer:bootRun
```

테스트 실행 시 Gradle 이 `spring.profiles.active=test`, `user.timezone=Asia/Seoul` 을 자동 주입하므로 별도 설정이 필요 없습니다.

## 새 도메인 추가 가이드 (commerce-api 기준)

`product` / `example` 와 동일한 패턴을 따르면 됩니다.

1. `domain.<x>` 에 `XModel(BaseEntity 상속)`, `XRepository(interface)`, `XService` 추가.
2. `infrastructure.<x>` 에 `XJpaRepository`, `XRepositoryImpl` 추가 (`XRepository` 구현).
3. `application.<x>` 에 `XFacade`, `XInfo` 추가.
4. `interfaces.api.<x>` 에 `XV1Controller`, `XV1Dto`, (선택) `XV1ApiSpec` 추가. 응답은 `ApiResponse.success/fail` 로 감쌉니다.
5. 도메인 규칙 위반은 `CoreException(ErrorType.*, "메시지")` 로 던지고, `ApiControllerAdvice` 가 처리하도록 둡니다.
6. 모델 단위 테스트 → 서비스 통합 테스트 → API E2E 테스트 순으로 추가 (TDD).
