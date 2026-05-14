# CLAUDE.md

## Project Overview

**loopers-java-spring-template** — 커머스 도메인 기반 멀티모듈 Java/Spring Boot 프로젝트.

## Tech Stack & Versions

| Category | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.4.4 |
| Dependency Mgmt | Spring Dependency Management | 1.1.7 |
| Cloud | Spring Cloud Dependencies | 2024.0.1 |
| Build Tool | Gradle (Kotlin DSL) | wrapper |
| ORM | Spring Data JPA + QueryDSL (Jakarta) | — |
| DB | MySQL | — |
| Cache | Spring Data Redis | — |
| Messaging | Spring Kafka | — |
| API Docs | SpringDoc OpenAPI | 2.7.0 |
| Monitoring | Micrometer (Prometheus) + Brave Tracing | — |
| Logging | Logback + Slack Appender | 1.6.1 |
| Lombok | Lombok | (managed by Spring BOM) |
| Test | JUnit 5, SpringMockK 4.0.2, Mockito 5.14.0, Instancio 5.0.2 |
| Test Infra | Testcontainers (MySQL, Redis, Kafka) | — |
| Code Coverage | JaCoCo | — |
| CI | GitHub Actions (qodo PR Agent) | — |

## Module Structure

```
root (loopers-java-spring-template)
├── apps/                        # 실행 가능 애플리케이션 (BootJar 활성)
│   ├── commerce-api/            # REST API 서버 (Web, Swagger, Actuator)
│   ├── commerce-batch/          # Spring Batch 애플리케이션
│   └── commerce-streamer/       # Kafka Consumer 애플리케이션
├── modules/                     # 인프라 모듈 (java-library)
│   ├── jpa/                     # JPA + QueryDSL + MySQL 설정, BaseEntity
│   ├── redis/                   # Redis 설정
│   └── kafka/                   # Kafka 설정
└── supports/                    # 공통 유틸리티 모듈
    ├── jackson/                 # Jackson 직렬화 설정
    ├── logging/                 # Actuator + Prometheus
    └── monitoring/              # Actuator + Prometheus
```

### Module Dependencies

- **commerce-api** → jpa, redis, jackson, logging, monitoring
- **commerce-batch** → jpa, redis, jackson, logging, monitoring
- **commerce-streamer** → jpa, redis, kafka, jackson, logging, monitoring

## Layered Architecture (commerce-api 기준)

```
interfaces/        → Controller, Dto, ApiSpec (API 진입점)
application/       → Facade, Info (유스케이스 조합, 도메인 간 조율)
domain/            → Service, Model, Repository interface (핵심 비즈니스 로직)
infrastructure/    → RepositoryImpl, JpaRepository (기술 구현체)
support/           → 공통 에러(CoreException, ErrorType) 등
```

### Layer Rules

- **interfaces** → `application` 만 의존. Controller는 ApiSpec 인터페이스를 구현.
- **application** → `domain` 만 의존. Facade(@Component)가 Service를 조합, Info(record)로 변환.
- **domain** → 외부 의존 없음. Repository는 인터페이스만 선언, Service(@Component)에서 비즈니스 로직 수행.
- **infrastructure** → `domain` Repository 인터페이스를 구현. JpaRepository를 내부 위임.

## Conventions

### Naming
- 패키지: `com.loopers`
- Entity: `{Name}Model extends BaseEntity`
- Repository 인터페이스: `{Name}Repository` (domain 패키지)
- Repository 구현체: `{Name}RepositoryImpl` (infrastructure 패키지)
- JPA Repository: `{Name}JpaRepository` (infrastructure 패키지)
- Facade: `{Name}Facade` (application 패키지)
- DTO 변환 객체: `{Name}Info` (application 패키지, record)
- Controller: `{Name}V{n}Controller`
- API Spec: `{Name}V{n}ApiSpec` (Swagger 문서화 인터페이스)
- Request/Response DTO: `{Name}V{n}Dto` 내부 static class

### API Response
- 통일된 응답 형식: `ApiResponse<T>` (meta + data)
- 성공: `ApiResponse.success(data)`
- 실패: `ApiResponse.fail(errorCode, errorMessage)`

### Error Handling
- `CoreException(ErrorType, message)` 패턴 사용
- `ErrorType`: enum (BAD_REQUEST, NOT_FOUND, CONFLICT, INTERNAL_ERROR)

### Entity
- 모든 Entity는 `BaseEntity`를 상속 (id, createdAt, updatedAt, deletedAt 자동 관리)
- Soft delete: `delete()` / `restore()` 메서드 (멱등)
- 유효성 검증: `guard()` 오버라이드 (PrePersist/PreUpdate 시 호출)
- JPA protected 기본 생성자 필수

### Test
- 테스트 시 timezone: `Asia/Seoul`, profile: `test`
- Testcontainers를 통한 통합 테스트 (MySQL, Redis, Kafka)
- testFixtures로 테스트 인프라 설정 공유 (DatabaseCleanUp, RedisCleanUp 등)
- maxParallelForks = 1 (순차 실행)

## Build & Run Commands

```bash
# 전체 빌드
./gradlew build

# 특정 모듈 빌드
./gradlew :apps:commerce-api:build

# 테스트
./gradlew test

# 특정 모듈 테스트
./gradlew :apps:commerce-api:test
```

## Profiles

`local` | `test` | `dev` | `qa` | `prd`
