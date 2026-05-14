# CLAUDE.md

이 문서는 본 저장소(`loopers-java-spring-template`)에서 Claude Code가 작업할 때 따라야 하는 가이드입니다.

---

## 프로젝트 개요

Loopers 에서 제공하는 Spring + Java 기반 멀티 모듈 템플릿 프로젝트입니다. 커머스 도메인을 가정한 `commerce-api`, `commerce-batch`, `commerce-streamer` 세 개의 실행 가능한 애플리케이션과, 이들이 공통으로 사용하는 인프라/부가 모듈로 구성되어 있습니다.

- **Root group**: `com.loopers`
- **Java toolchain**: 21
- **빌드**: Gradle (Kotlin DSL), 멀티 프로젝트
- **권장 Timezone**: `Asia/Seoul` (테스트 JVM 옵션에 강제됨)

---

## 기술 스택 및 버전

`gradle.properties` / 루트 `build.gradle.kts` / 각 서브모듈 `build.gradle.kts` 기준입니다.

### Language / Build
- **Java**: 21 (toolchain)
- **Kotlin**: 2.0.20 (`kotlinVersion`) — Jackson Kotlin module 등 일부 의존성에서 사용
- **Gradle Plugins**
  - Spring Boot `3.4.4` (`springBootVersion`)
  - Spring Dependency Management `1.1.7` (`springDependencyManagementVersion`)
  - ktlint `12.1.2` / ktlint `1.0.1`
  - Jacoco (서브프로젝트 전부 적용, XML 리포트 필수)

### Spring / Web
- Spring Boot 3.4.4 (`spring-boot-starter`, `spring-boot-starter-web`, `spring-boot-starter-actuator`, `spring-boot-starter-validation`)
- Spring Cloud BOM `2024.0.1` (`springCloudDependenciesVersion`)
- springdoc OpenAPI `2.7.0` (`springDocOpenApiVersion`) — Swagger UI `/swagger-ui.html`
- Spring Batch (`spring-boot-starter-batch`) — `commerce-batch`
- Spring for Apache Kafka (`spring-kafka`) — `commerce-streamer`

### Persistence / Infra
- Spring Data JPA + Hibernate (`spring-boot-starter-data-jpa`)
- QueryDSL JPA (`querydsl-jpa::jakarta`, jakarta annotation processor)
- MySQL JDBC (`mysql-connector-j`, runtime)
- Spring Data Redis (`spring-boot-starter-data-redis`)

### Observability
- Micrometer Prometheus Registry
- Micrometer Tracing Bridge (Brave)
- Logback Slack Appender `1.6.1` (`slackAppenderVersion`)

### Test
- JUnit 5 (`spring-boot-starter-test`, `junit-platform-launcher`)
- AssertJ (Spring Boot 기본 포함)
- Mockito `5.14.0` (`mockitoVersion`)
- springmockk `4.0.2` (`springMockkVersion`)
- Instancio JUnit `5.0.2` (`instancioJUnitVersion`)
- Testcontainers (MySQL, Redis, Kafka) + `spring-boot-testcontainers`
- Spring Batch Test (`spring-batch-test`)

### Lombok
- 전역 적용 (`org.projectlombok:lombok` + annotation processor)

---

## 모듈 구조

```
Root
├── apps                ( 실행 가능한 SpringBootApplication )
│   ├── commerce-api        # REST API (web, JPA, Redis, OpenAPI)
│   ├── commerce-batch      # Spring Batch (JPA, Redis)
│   └── commerce-streamer   # Kafka 컨슈머/스트리머 (web, JPA, Redis, Kafka)
├── modules             ( 도메인 비의존, reusable configuration )
│   ├── jpa                 # DataSource/JPA/QueryDSL 설정 + BaseEntity + MySQL Testcontainers fixture
│   ├── redis               # Redis 설정 + Redis Testcontainers fixture
│   └── kafka               # Spring Kafka 설정 + Kafka Testcontainers fixture
└── supports            ( 부가 기능 add-on )
    ├── jackson             # ObjectMapper / JSR-310 / Kotlin 모듈 구성
    ├── logging             # 로깅 설정 (+ Slack appender)
    └── monitoring          # Actuator + Prometheus 노출
```

### 의존성 규칙 (현재 설정 기준)
- `apps/*` 만 `modules/*` 와 `supports/*` 에 의존합니다.
- `modules/*` 는 도메인을 모릅니다 — 재사용 가능한 설정/유틸만 포함합니다.
- `supports/*` 는 cross-cutting add-on (logging, monitoring, jackson) 으로만 한정합니다.
- `apps/*` 는 `BootJar` 만 빌드, `modules/*`·`supports/*` 는 일반 `Jar` 만 빌드합니다.

### commerce-api 내부 패키지 (DDD 풍 레이어)
```
com.loopers
├── interfaces.api.*          # Controller / DTO / ApiSpec / ApiResponse / ControllerAdvice
├── application.*             # Facade + Info (유스케이스 조립)
├── domain.*                  # Model(Entity) / Service / Repository(interface)
├── infrastructure.*          # JpaRepository / RepositoryImpl
└── support.error             # CoreException / ErrorType
```
- 응답은 항상 `ApiResponse<T>` 로 감싸 반환합니다 (`success` / `fail` + `Metadata`).
- 예외는 `CoreException(ErrorType, customMessage?)` 로 던지고 `ApiControllerAdvice` 가 일관된 응답으로 변환합니다.
- 엔티티는 `com.loopers.domain.BaseEntity` 를 상속 (id, createdAt, updatedAt, deletedAt, `delete()`/`restore()` 멱등 동작 제공).

---

## 실행 / 인프라

### 로컬 인프라
```shell
docker-compose -f ./docker/infra-compose.yml up
```
### 모니터링 (Prometheus + Grafana)
```shell
docker-compose -f ./docker/monitoring-compose.yml up
# http://localhost:3000  (admin / admin)
```
### 프로필
- `application.yml` 분할 프로필: `local`, `test`, `dev`, `qa`, `prd`
- 테스트 실행 시 자동으로 `spring.profiles.active=test`, TZ `Asia/Seoul` 적용 (`build.gradle.kts`).

### API 호출 예시
- `http/commerce-api/**.http` 에 IntelliJ HTTP Client 포맷으로 정리. 환경 변수는 `http/http-client.env.json`.

---

## 개발 규칙

### 진행 Workflow - 증강 코딩

- **대원칙** : 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행.
- **중간 결과 보고** : AI 가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입.
- **설계 주도권 유지** : AI 가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행.

### 개발 Workflow - TDD (Red > Green > Refactor)

- **테스트 필수 구현**: 지정된 단위 테스트(Unit), 통합 테스트(Integration), E2E 테스트 케이스를 필수로 구현하고 모든 테스트 통과를 목표로 함.
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

---

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
