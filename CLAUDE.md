# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 개발 규칙

### 진행 Workflow - 증강 코딩
- **대원칙** : 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행.
- **중간 결과 보고** : AI 가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입.
- **설계 주도권 유지** : AI 가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행.

### 개발 Workflow - TDD (Red > Green > Refactor)
- 모든 테스트는 3A 원칙으로 작성할 것 (Arrange - Act - Assert)

#### 1. Red Phase : 실패하는 테스트 먼저 작성
- 요구사항을 만족하는 기능 테스트 케이스 작성

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
- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용한 구현을 하지 말 것
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

---

## 프로젝트 개요

Loopers 부트캠프 Spring Java 템플릿 프로젝트. Java 21 + Spring Boot 3.4.4 기반 멀티 모듈 구성.

## 빌드 & 실행

```bash
# 인프라 실행 (MySQL, Redis, Kafka)
docker-compose -f ./docker/infra-compose.yml up

# 모니터링 실행 (Prometheus, Grafana - http://localhost:3000, admin/admin)
docker-compose -f ./docker/monitoring-compose.yml up

# 전체 빌드
./gradlew build

# 테스트 실행 (timezone=Asia/Seoul, profile=test 자동 적용)
./gradlew test

# 특정 모듈 테스트
./gradlew :apps:commerce-api:test

# JaCoCo 커버리지 리포트
./gradlew test jacocoTestReport
```

## 모듈 구조

```
apps/commerce-api       # REST API 서버 (port 8080)
apps/commerce-batch     # Spring Batch 배치 애플리케이션
apps/commerce-streamer  # Kafka 컨슈머

modules/jpa             # DataSource, JPA, QueryDSL 설정 + BaseEntity
modules/redis           # Redis master/replica 설정
modules/kafka           # Kafka consumer 설정

supports/jackson        # ObjectMapper 설정
supports/logging        # Logback 설정
supports/monitoring     # Actuator, Prometheus 설정
```

## 아키텍처 패턴 (commerce-api 기준)

레이어 순서: `interfaces` → `application` → `domain` → `infrastructure`

- **interfaces/api** : Controller, Dto, ApiResponse 래퍼
- **application** : Facade (여러 도메인 서비스 조합)
- **domain** : Model(Entity), Repository 인터페이스, Service
- **infrastructure** : JpaRepository, RepositoryImpl

### 핵심 클래스

- `BaseEntity` — `id`, `createdAt`, `updatedAt`, `deletedAt` 자동 관리. soft delete 지원 (`delete()` / `restore()`). `guard()` 오버라이드로 persist/update 시점 검증.
- `CoreException(ErrorType, message)` — 도메인 예외. `ErrorType`은 `BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR`.
- `ApiResponse<T>` — 응답 래퍼. `meta(result, errorCode, message)` + `data`. `ApiResponse.success(data)` / `ApiResponse.fail(...)` 사용.

### 도메인 모델 작성 규칙

생성자에서 유효성 검증 (null/blank 체크 → `CoreException` throw). Repository는 도메인 인터페이스로 선언, infrastructure에서 구현.

```java
// Repository 인터페이스 (domain layer)
public interface ProductRepository {
    Optional<ProductModel> find(Long id);
    ProductModel save(ProductModel product);
}

// 구현 (infrastructure layer)
public class ProductRepositoryImpl implements ProductRepository { ... }
```

## 테스트 전략

테스트 실행 시 `spring.profiles.active=test` + Testcontainers(MySQL, Redis) 자동 사용.

| 종류 | 위치 | 특징 |
|------|------|------|
| 단위 테스트 | `domain/*ModelTest` | 도메인 로직만, Spring 컨텍스트 없음 |
| 통합 테스트 | `domain/*ServiceIntegrationTest` | `@SpringBootTest`, 실제 DB |
| E2E 테스트 | `interfaces/api/*ApiE2ETest` | `TestRestTemplate`, 실제 HTTP |

**공통 패턴:**
- `@AfterEach`에서 `databaseCleanUp.truncateAllTables()` 호출
- `@Nested` + `@DisplayName`으로 계층적 테스트 구조
- 메서드명: `행위_when조건()` 형식 (예: `returnsProduct_whenValidIdIsProvided`)
