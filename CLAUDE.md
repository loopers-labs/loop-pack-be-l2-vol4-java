# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### 인프라 실행 (테스트/로컬 실행 전 필수)
```shell
docker-compose -f ./docker/infra-compose.yml up
```

### 전체 빌드 및 테스트
```shell
./gradlew build
./gradlew test
```

### 특정 모듈 테스트
```shell
./gradlew :apps:commerce-api:test
./gradlew :apps:commerce-batch:test
```

### 단일 테스트 클래스 실행
```shell
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.example.ExampleModelTest"
```

### 단일 테스트 메서드 실행
```shell
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.example.ExampleModelTest.Create.createsExampleModel_whenNameAndDescriptionAreProvided"
```

### 애플리케이션 실행
```shell
./gradlew :apps:commerce-api:bootRun
```

## 아키텍처

### 멀티 모듈 구조
- `apps/` — 실행 가능한 SpringBootApplication. `commerce-api`(REST API), `commerce-batch`(배치), `commerce-streamer`(Kafka 컨슈머)
- `modules/` — 도메인에 독립적인 재사용 설정. `jpa`, `redis`, `kafka`
- `supports/` — 부가 기능 애드온. `jackson`, `monitoring`, `logging`

### `commerce-api` 레이어 구조

4개 레이어가 엄격히 분리되어 있다:

```
interfaces/api      → Controller, Dto, ApiSpec (Swagger)
application         → Facade, Info (레이어 간 데이터 전달 record)
domain              → Service, Model (Entity), Repository (인터페이스)
infrastructure      → JpaRepository, RepositoryImpl
```

- **Controller**는 Facade만 호출한다. Service를 직접 호출하지 않는다.
- **Facade**는 여러 Service를 조합하는 역할이다. 단일 Service 호출도 Facade를 거친다.
- **Service**는 도메인 로직을 담당하며 `@Transactional`을 선언한다.
- **Repository**는 도메인 레이어에 인터페이스로 정의되고, `infrastructure`에서 구현한다 (의존성 역전).
- **Model**은 JPA Entity이자 도메인 객체다. 생성자에서 유효성 검증을 수행하며 유효하지 않으면 `CoreException`을 던진다.

### API 응답 형식
모든 응답은 `ApiResponse<T>` record를 사용한다:
```json
{ "meta": { "result": "SUCCESS", "errorCode": null, "message": null }, "data": { ... } }
```
에러 시 `ApiControllerAdvice`가 `CoreException`을 잡아 `ApiResponse.fail()`로 변환한다.

### 예외 처리
- 비즈니스 예외는 `CoreException(ErrorType, customMessage)` 사용
- `ErrorType` enum: `BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR` (각각 HTTP 상태코드 매핑됨)

### BaseEntity
모든 Entity는 `modules/jpa`의 `BaseEntity`를 상속한다. `id`(AUTO_INCREMENT), `createdAt`, `updatedAt`, `deletedAt`을 자동 관리한다. 소프트 딜리트는 `delete()` / `restore()` 메서드로 처리하며 둘 다 멱등하게 동작한다.

### API 스펙 분리
Controller는 `implements XxxV1ApiSpec` 형태로 Swagger 어노테이션을 인터페이스에 분리한다. Controller 본문에는 `@Operation` 등 Swagger 어노테이션을 작성하지 않는다.

## 테스트 관행

테스트는 3단계로 구분된다:

| 종류 | 위치 | 어노테이션 | 특징 |
|------|------|-----------|------|
| 단위 테스트 | `domain/XxxModelTest` | 없음 (순수 Java) | Model 생성·유효성 검증 |
| 통합 테스트 | `domain/XxxServiceIntegrationTest` | `@SpringBootTest` | 실제 DB(Testcontainers), `DatabaseCleanUp`으로 격리 |
| E2E 테스트 | `interfaces/api/XxxApiE2ETest` | `@SpringBootTest(webEnvironment=RANDOM_PORT)` | `TestRestTemplate` 사용, 실제 HTTP 요청 |

- 테스트 메서드 이름은 `동사_when조건` 패턴을 따른다 (예: `returnsExampleInfo_whenValidIdIsProvided`)
- 각 테스트는 `// arrange / act / assert` 주석으로 구분한다
- 통합·E2E 테스트는 `@AfterEach`에서 `databaseCleanUp.truncateAllTables()`를 호출해 DB를 초기화한다
- `@Nested` + `@DisplayName`으로 테스트를 그룹화한다

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

## 현재 구현 과제 (.codeguide/loopers-1-week.md)

회원 가입, 내 정보 조회, 포인트 조회 기능을 구현해야 한다. 각 기능마다 단위/통합/E2E 테스트를 필수로 구현하고 통과시켜야 한다. `apps/commerce-api` 내에 `example` 패키지의 구조를 참고해 동일한 레이어 패턴으로 작성한다.
