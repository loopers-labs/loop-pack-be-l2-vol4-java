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

## 도메인 & 객체 설계 전략

- 도메인 객체는 비즈니스 규칙을 캡슐화해야 합니다.
- 애플리케이션 서비스는 서로 다른 도메인을 조립해, 도메인 로직을 조정하여 기능을 제공해야 합니다.
- 규칙이 여러 서비스에 나타나면 도메인 객체에 속할 가능성이 높습니다.
- 각 기능에 대한 책임과 결합도에 대해 개발자의 의도를 확인하고 개발을 진행합니다.

### Entity / Value Object / Domain Service 구분

- **Entity** — ID로 동일성을 판단하며, 상태가 변하고 연속성을 가지는 객체 (예: `User`, `Order`, `Product`)
- **Value Object** — 값 자체로 동일성을 판단하며 불변(immutable). 비교/계산 중심 (예: `Money`, `Quantity`, `Address`)
- **Domain Service** — 상태를 갖지 않고, 단일 도메인 객체에 두기 애매한 여러 도메인 간 협력 로직을 담는 객체
- 원시 타입(`Long`, `int` 등)에 비즈니스 규칙이 반복적으로 붙기 시작하면 VO 도입을 검토합니다.
- "X하는 놈(Manager/Doer)"처럼 상태/정체성 없이 연산만 하는 객체는 도메인이 아니라 서비스로 분리합니다.

### 도메인 로직 위치 휴리스틱

판단 순서:
1. **단일 객체 내부에서 결정되는 규칙인가?** → Entity 메서드
2. **여러 도메인 객체가 협력해야 하는가?** → Domain Service
3. **유스케이스 흐름의 조율인가?** → Application Layer (Facade)
4. **외부 기술/시스템에 의존하는 작업인가?** → Infrastructure

### Application Layer 경량 원칙

- Facade는 **도메인 호출 조율 + DTO 매핑**까지만 담당합니다.
- Facade에 `if`로 비즈니스 조건 분기가 쌓이기 시작하면 도메인으로 옮길 시점입니다.
- 비즈니스 규칙/계산/검증은 Entity, VO, Domain Service로 위임합니다.

---

## 아키텍처, 패키지 구성 전략

- 본 프로젝트는 레이어드 아키텍처를 따르며, DIP (의존성 역전 원칙) 을 준수합니다.
- API request, response DTO와 응용 레이어의 DTO는 분리해 작성하도록 합니다.
- 패키징 전략은 4개 레이어 패키지를 두고, 하위에 도메인 별로 패키징하는 형태로 작성합니다.
  - 예시
    > /interfaces/api (presentation 레이어 - API)
      /application/.. (application 레이어 - 도메인 레이어를 조합해 사용 가능한 기능을 제공)
      /domain/.. (domain 레이어 - 도메인 객체 및 엔티티, Repository 인터페이스가 위치)
      /infrastructure/.. (infrastructure 레이어 - JPA, Redis 등을 활용해 Repository 구현체를 제공)

### DTO 분리 규칙

- `interfaces/api/{domain}/{Domain}V1Dto` — API 요청/응답 DTO (예: `RegisterRequest`, `UserResponse`)
- `application/{domain}/{Domain}Info` — Application Layer 결과 객체. Domain Entity를 외부로 노출하지 않습니다.
- Controller는 Facade가 반환한 `Info`를 받아 `Response DTO`로 변환해 응답합니다.
- Domain Entity는 Application Layer 경계를 넘지 않습니다.

### Aggregate 경계 규칙

- DB FK 제약은 **같은 애그리거트 내부에서만** 사용합니다 (예: `order_items.order_id`).
- 다른 애그리거트 간 참조는 **Long ID 참조**로 처리하고, 정합성은 애플리케이션 레벨에서 보장합니다.
- 사유: 락 경쟁 회피, 마이그레이션 유연성, 추후 샤딩 대비.

### 단위 테스트 의존성 처리

- 단위 테스트는 외부 의존성을 **Fake/Stub**으로 대체합니다. (Domain Layer의 Repository Interface 활용)
- Mockito는 협력 객체의 **호출 검증**이 필요한 경우에 한정해 사용하고, 데이터 흐름 검증에는 Fake 구현체를 우선합니다.
- Entity, VO, Domain Service 단위 테스트는 Spring 컨텍스트 없이 작성합니다.

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
    Optional<ProductModel> findById(Long id);
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
