# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Tech Stack & Versions

| 항목 | 버전 |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.4 |
| Spring Cloud | 2024.0.1 |
| Kotlin (build scripts) | 2.0.20 |
| MySQL | 8.0 |
| Redis | 7.0 |
| Kafka | 3.5.1 (KRaft mode) |
| QueryDSL | Spring Boot BOM 관리 (jakarta) |
| Testcontainers | Spring Boot BOM 관리 |

## Commands

```bash
# 전체 빌드
./gradlew build

# 특정 모듈 빌드
./gradlew :apps:commerce-api:build

# 전체 테스트
./gradlew test

# 특정 모듈 테스트
./gradlew :apps:commerce-api:test

# 특정 테스트 클래스 실행
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.example.ExampleServiceIntegrationTest"

# JaCoCo 리포트 생성 (테스트 후 실행)
./gradlew jacocoTestReport

# 로컬 인프라 실행 (MySQL, Redis master/replica, Kafka, Kafka-UI)
docker-compose -f ./docker/infra-compose.yml up

# 모니터링 실행 (Prometheus + Grafana — http://localhost:3000, admin/admin)
docker-compose -f ./docker/monitoring-compose.yml up
```

## Multi-Module Architecture

```
Root
├── apps/          — 실행 가능한 SpringBootApplication
│   ├── commerce-api       (REST API, Tomcat)
│   ├── commerce-batch     (Spring Batch)
│   └── commerce-streamer  (Kafka consumer/producer)
├── modules/       — 재사용 가능한 infrastructure 설정 (java-library + java-test-fixtures)
│   ├── jpa        (Spring Data JPA, QueryDSL, MySQL HikariCP DataSource)
│   ├── redis      (Spring Data Redis)
│   └── kafka      (Spring Kafka)
└── supports/      — 부가 기능 add-on (java-library)
    ├── jackson    (ObjectMapper 설정)
    ├── logging    (Logback + Slack appender, Micrometer Brave tracing)
    └── monitoring (Actuator + Prometheus)
```

- `apps` 하위 모듈만 `BootJar`가 활성화되고, `modules`/`supports`는 일반 `Jar`로 빌드됩니다.
- `modules`는 `testFixtures`를 제공합니다. `apps`에서 `testFixtures(project(":modules:jpa"))` 형태로 Testcontainers 기반 테스트 픽스처를 재사용합니다.
- 각 `modules`/`supports`는 자체 `*.yml`을 가지며, `apps`의 `application.yml`에서 `spring.config.import`로 포함합니다.

## Layered Architecture (commerce-api 기준)

```
interfaces/api/     — Controller, Dto (입출력 변환)
application/        — Facade (유스케이스 조합), Info (응답 DTO)
domain/             — Service, Model (엔티티), Repository (인터페이스)
infrastructure/     — JpaRepository, RepositoryImpl (도메인 인터페이스 구현)
support/error/      — CoreException, ErrorType
```

- `Controller` → `Facade` → `Service` → `Repository` 방향으로만 의존합니다.
- `Facade`는 여러 `Service`를 조합하며 트랜잭션 경계 바깥에 위치합니다. 트랜잭션은 `Service` 레이어에서 관리합니다.
- 도메인 모델(`ProductModel` 등)은 `BaseEntity`를 상속하며, 생성자 및 메서드 내부에서 `CoreException`으로 유효성 검증을 직접 수행합니다.

## Error Handling

- 모든 비즈니스 예외는 `CoreException(ErrorType, customMessage)`를 사용합니다.
- `ErrorType`은 `HttpStatus`, `code`(HTTP 표준 reason phrase), `message`(기본 메시지)를 가집니다.
- `ApiControllerAdvice`가 `CoreException` 및 Spring MVC 예외를 `ApiResponse` 포맷으로 일관되게 처리합니다.
- 응답 포맷: `ApiResponse<T> { success, code, message, data }`

## BaseEntity

모든 엔티티는 `modules/jpa`의 `BaseEntity`를 상속합니다.

- `id` (IDENTITY 전략), `createdAt`, `updatedAt`, `deletedAt` 자동 관리
- `@PrePersist`/`@PreUpdate` 시점에 `guard()` 호출 — 서브클래스에서 오버라이드해 유효성 검증 추가 가능
- 소프트 삭제: `delete()` / `restore()` 메서드 제공 (멱등 보장)

## Test Conventions

테스트는 세 계층으로 구분합니다.

| 계층 | 어노테이션 | 특징 |
|---|---|---|
| 단위 테스트 | (없음, 순수 Java) | 도메인 모델/객체 생성 규칙 검증 |
| 통합 테스트 | `@SpringBootTest` | Testcontainers 기반 실제 DB/Redis/Kafka 사용 |
| E2E 테스트 | `@SpringBootTest(webEnvironment = RANDOM_PORT)` | `TestRestTemplate`으로 HTTP 요청 |

- 각 테스트 클래스는 `@AfterEach`에서 `DatabaseCleanUp.truncateAllTables()`로 DB를 초기화합니다.
- 테스트 프로파일은 `spring.profiles.active=test`로 자동 설정됩니다.
- 테스트는 `maxParallelForks = 1`로 순차 실행됩니다.
- `Instancio`를 활용한 랜덤 객체 생성, `SpringMockK`(Kotlin mockk 통합)를 테스트에서 사용할 수 있습니다.

## Spring Profiles

`local` → `dev` → `qa` → `prd` 순서로 운영하며, 각 모듈 yml에 프로파일별 설정이 분리되어 있습니다.

- `local`/`test`: `ddl-auto: create`, `show-sql: true`
- `prd`: Swagger(`springdoc.api-docs.enabled: false`) 비활성화

## Infrastructure (local)

| 서비스 | 포트 |
|---|---|
| MySQL | 3306 |
| Redis master | 6379 |
| Redis replica (read-only) | 6380 |
| Kafka | 9092 (내부), 19092 (호스트) |
| Kafka-UI | 9099 |
| Grafana | 3000 |

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