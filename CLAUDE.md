# CLAUDE.md

이 문서는 Claude Code(claude.ai/code)가 본 리포지토리에서 작업할 때 참고할 가이드입니다.

## 프로젝트 개요

Loopers 에서 제공하는 Spring + Java 기반 멀티 모듈 백엔드 템플릿 프로젝트입니다.
`commerce-api`, `commerce-batch`, `commerce-streamer` 세 가지 실행 가능한 애플리케이션을 중심으로 구성됩니다.

- 루트 프로젝트명: `loopers-java-spring-template`
- 그룹: `com.loopers`

## 기술 스택 및 버전

`gradle.properties` / `build.gradle.kts` 기준 핵심 버전:

| 영역 | 항목 | 버전 |
| --- | --- | --- |
| Language | Java (toolchain) | **21** |
| Language | Kotlin | 2.0.20 (`kotlinVersion`) |
| Framework | Spring Boot | **3.4.4** (`springBootVersion`) |
| Framework | Spring Dependency Management | 1.1.7 |
| Framework | Spring Cloud BOM | 2024.0.1 |
| Build | Gradle Kotlin DSL | (wrapper 사용) |
| Lint | ktlint plugin | 12.1.2 / ktlint 1.0.1 |
| API Docs | springdoc-openapi | 2.7.0 |
| Test | springmockk | 4.0.2 |
| Test | mockito-core | 5.14.0 |
| Test | instancio-junit | 5.0.2 |
| Logging | logback-slack-appender | 1.6.1 |

`build.gradle.kts` (root) 에서 모든 subproject 에 공통 적용되는 의존성/플러그인:

- 플러그인: `java`, `org.springframework.boot`(apply false on root, subprojects에서 apply), `io.spring.dependency-management`, `jacoco`
- 공통 dependency: `spring-boot-starter`, `jackson-datatype-jsr310`, `lombok`(+ annotationProcessor), validation runtime
- 공통 테스트: `spring-boot-starter-test`, `springmockk`, `mockito-core`, `instancio-junit`, `spring-boot-testcontainers`, `testcontainers` + `junit-jupiter`, `mysql-connector-j`(testRuntime)
- 테스트 설정: `useJUnitPlatform()`, `maxParallelForks=1`, `user.timezone=Asia/Seoul`, `spring.profiles.active=test`, `-Xshare:off`
- Jar 정책: 기본 `Jar` 활성 / `BootJar` 비활성, `apps/*` 하위 모듈은 반대로 `BootJar` 만 활성

## 모듈 구조

```
Root
├── apps        (실행 가능한 SpringBootApplication)
│   ├── commerce-api        — REST API 서버
│   ├── commerce-batch      — Spring Batch 잡 실행기
│   └── commerce-streamer   — Kafka Consumer 애플리케이션
├── modules     (재사용 가능한 configuration. 도메인 비의존)
│   ├── jpa     — Spring Data JPA + QueryDSL + MySQL + Testcontainers
│   ├── redis   — Spring Data Redis + Testcontainers
│   └── kafka   — Spring Kafka + Testcontainers
└── supports    (부가 기능 add-on)
    ├── jackson    — Jackson 설정 (jsr310, kotlin module)
    ├── logging    — Actuator + Micrometer(Prometheus, Brave) + Slack appender
    └── monitoring — Actuator + Micrometer Prometheus
```

`settings.gradle.kts` 에 등록된 정식 프로젝트 경로:
`:apps:commerce-api`, `:apps:commerce-batch`, `:apps:commerce-streamer`,
`:modules:jpa`, `:modules:redis`, `:modules:kafka`,
`:supports:jackson`, `:supports:logging`, `:supports:monitoring`

루트의 컨테이너 프로젝트(`apps`, `modules`, `supports`)는 `tasks.configureEach { enabled = false }` 로 task 실행을 비활성화합니다.

### apps 모듈별 의존 요약

- **commerce-api**: `modules:jpa`, `modules:redis`, `supports:{jackson,logging,monitoring}` + `spring-boot-starter-web`, `spring-boot-starter-actuator`, `springdoc-openapi-starter-webmvc-ui`, QueryDSL APT
- **commerce-streamer**: `modules:jpa`, `modules:redis`, `modules:kafka`, `supports:*` + `web`, `actuator`, QueryDSL APT
- **commerce-batch**: `modules:jpa`, `modules:redis`, `supports:*` + `spring-boot-starter-batch` (+ `spring-batch-test`), QueryDSL APT

`modules:jpa` / `modules:redis` / `modules:kafka` 는 `java-test-fixtures` 플러그인을 사용해 Testcontainers 기반의 테스트 픽스처(`DatabaseCleanUp`, `MySqlTestContainersConfig`, `RedisCleanUp`, `RedisTestContainersConfig` 등)를 제공합니다. 앱 모듈은 `testImplementation(testFixtures(project(...)))` 로 이를 가져다 씁니다.

### commerce-api 패키지 레이아웃 (com.loopers)

레이어드/헥사고날 컨벤션:

```
com.loopers
├── interfaces       — Controller, DTO, API spec, ControllerAdvice
├── application      — Facade (Use case 조합)
├── domain           — Model, Service, Repository(인터페이스)
├── infrastructure   — JpaRepository, RepositoryImpl (도메인 Repository 구현)
└── support          — 공통(ErrorType, CoreException 등)
```

현재 예시 도메인: `example`, `product`.

## 실행 / 인프라

`local` 프로필 동작에 필요한 인프라는 docker-compose 로 제공됩니다.

```bash
# 인프라 (MySQL 8.0, Redis 7.0 master/replica, Kafka 3.5.1 KRaft, kafka-ui)
docker-compose -f ./docker/infra-compose.yml up

# 모니터링 (Prometheus + Grafana, http://localhost:3000, admin/admin)
docker-compose -f ./docker/monitoring-compose.yml up
```

주요 포트: MySQL `3306`, Redis master `6379` / replica `6380`, Kafka `9092`(내부) · `19092`(호스트), kafka-ui `9099`.

Spring 프로필: `local`, `test`, `dev`, `qa`, `prd`. `application.yml` 에서 `jpa.yml`, `redis.yml`, `logging.yml`, `monitoring.yml` 을 `spring.config.import` 로 합성합니다.

## 자주 쓰는 Gradle 명령

```bash
./gradlew build                              # 전체 빌드
./gradlew :apps:commerce-api:bootRun         # API 앱 실행
./gradlew :apps:commerce-batch:bootRun       # 배치 앱 실행
./gradlew :apps:commerce-streamer:bootRun    # 스트리머 앱 실행
./gradlew test                               # 전체 테스트
./gradlew :apps:commerce-api:test            # 모듈 단위 테스트
./gradlew jacocoTestReport                   # 커버리지 리포트(XML)
```

테스트는 JUnit Platform 사용, JVM 옵션 `-Xshare:off`, 타임존 `Asia/Seoul`, 프로필 `test` 가 기본 적용됩니다.

## 작업 시 유의 사항

- 새로운 도메인을 추가할 때는 `commerce-api` 의 4-layer 컨벤션(`interfaces` / `application` / `domain` / `infrastructure`)을 따릅니다.
- JPA 엔티티의 공통 베이스는 `modules:jpa` 의 `com.loopers.domain.BaseEntity` 입니다.
- 통합 테스트는 Testcontainers 기반 픽스처(`MySqlTestContainersConfig`, `RedisTestContainersConfig`) 를 활용합니다.
- 컨트롤러 응답 포맷은 `interfaces.api.ApiResponse`, 예외는 `support.error.CoreException` + `ErrorType` 으로 통일되어 있습니다.
- 빌드 결과의 `version` 은 git short hash 로 자동 셋업됩니다(`getGitHash()`), 별도 지정이 없으면 `init`.


## 개발 규칙

### 진행 Workflow - 증강 코딩

- **대원칙** : 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행한다.
- **중간 결과 보고** : AI가 반복적인 실패를 겪거나, 접근 방식 변경이 필요하거나, 요구사항 외 기능이 필요하다고 판단될 경우 현재 상황과 다음 행동을 보고하고 개발자의 승인을 요청한다.
- **설계 주도권 유지** : AI는 임의판단으로 설계나 요구사항을 변경하지 않는다. 방향성에 대한 제안은 가능하지만 개발자의 승인을 받은 후 수행한다.
- **To-Do 작성** : 제공받은 요구사항과 기능 구현 리스트를 기준으로 엣지 케이스를 포함한 기능별 테스트 케이스를 `check-list.md`에 작성한다.
- **금지 사항** : 요청하지 않은 기능 구현, 테스트 삭제, 테스트 스킵, 테스트 기대값 완화는 개발자의 명시적 승인 없이는 수행하지 않는다.

### 개발 Workflow - TDD (Red > Green > Refactor)

- 기능을 작은 증가분으로 정의하는 실패하는 테스트부터 시작한다.
- 테스트를 통과시키는 데 충분한 코드만 작성한다. 그 이상은 하지 않는다.
- 모든 테스트가 통과하면, 리팩터링 필요성을 검토한다.
- 리팩터링은 동작 변경 없이 수행한다.
- 새로운 기능에 대해 이 사이클을 반복한다.

### 예시 워크플로우

1. 기능의 작은 부분을 위한 단순한 실패하는 테스트를 작성한다.
2. 테스트를 통과시키는 데 필요한 최소한만 구현한다.
3. 테스트를 실행해 통과를 확인한다.
4. 필요한 구조적 정리를 수행하고, 변경 후 테스트를 다시 실행한다.
5. 각 기능 단위가 완료되면 개발자의 검증을 받은 후 `check-list.md`에 완료 표시한다.
6. `check-list.md`에 완료되지 않은 항목이 없을 때까지 위 과정을 반복한다.

## 주의사항

### 1. Never Do

- 실제 동작하지 않는 코드나 Mock 데이터에 의존한 가짜 구현을 하지 않는다.
    - 단위 테스트에서 의존성 격리를 위한 Mock 사용은 허용한다.
    - 실제 구현은 Mock 데이터가 아닌 실제 입력, 실제 흐름, 실제 의존성을 기준으로 작성한다.
- null-safety를 고려하지 않은 코드를 작성하지 않는다.
    - Java의 경우 nullable한 반환값은 Optional 사용을 우선 고려한다.
- 임시 디버깅용 println 코드를 남기지 않는다.


### 2. Recommendation

- 핵심 API는 실제 호출 기반 E2E 테스트 작성을 고려한다.
- 중복이 명확하거나 확장 가능성이 확인된 경우 재사용 가능한 객체로 설계한다.
    - 불필요한 추상화나 과도한 일반화는 피한다.
- 성능 이슈가 예상될 경우 최적화 대안과 트레이드오프를 제안한다.
    - 개발자의 승인 없이 복잡한 성능 최적화를 임의로 적용하지 않는다.
- 개발 완료된 API는 수동 검증이 가능하도록 `http/**.http` 경로에 HTTP 요청 예시를 분류해 작성한다.

### 3. Priority

1. 실제 동작하는 해결책만 고려한다.
2. null-safety와 thread-safety를 고려한다.
3. 테스트 가능한 구조로 설계한다.
4. 기존 코드 패턴을 분석한 후 일관성을 유지한다.


## 커밋 규칙

- **커밋 실행 제한** : AI는 개발자의 명시적 요청 또는 승인 없이 커밋을 생성하지 않는다.
- **커밋 단위** : 커밋은 의미 있는 작업 단위로 진행한다.
- **변경 분리** : 기능 변경과 리팩터링은 가능한 한 별도 커밋으로 분리한다.
- **커밋 전 확인** : 커밋 전 관련 테스트가 통과했는지 확인한다.
- **커밋 전 보고** : 커밋 전 변경 내용을 요약하고 개발자의 승인을 요청한다.
- **커밋 메시지 형식** : 커밋 메시지는 `prefix: 변경 내용 요약` 형식을 사용한다.

### Commit Message Prefix
- feat: 새로운 기능 추가
- fix: 버그 수정
- refactor: 리팩토링 (기능 변경 없음)
- docs: 문서 수정
- test: 테스트 코드