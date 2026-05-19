# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 응답 언어

모든 답변은 반드시 한국어로만 작성한다.

## 개요

Loopers Spring Java 템플릿 — Spring Boot 3.4.4, Java 21 기반의 Gradle 멀티 모듈 프로젝트. 로컬 개발 환경은 Docker Compose로 인프라를 구성한다.
모듈 구조 및 아키텍처 상세는 `PROJECT_STRUCTURE.md` 참고.

## 명령어

### 빌드
```shell
./gradlew build
# 특정 모듈만 빌드
./gradlew :apps:commerce-api:build
```

### 테스트
```shell
# 전체 테스트
./gradlew test

# 특정 모듈 테스트
./gradlew :apps:commerce-api:test

# 특정 테스트 클래스
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ExampleV1ApiE2ETest"

# 특정 테스트 메서드
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.ExampleV1ApiE2ETest.Get.returnsExampleInfo_whenValidIdIsProvided"
```

테스트는 `spring.profiles.active=test`, `user.timezone=Asia/Seoul`, `maxParallelForks=1` 조건으로 실행된다.

### 커버리지
```shell
./gradlew test jacocoTestReport
```

### 로컬 인프라
```shell
# 앱 실행 전 필수
docker-compose -f ./docker/infra-compose.yml up

# 모니터링 선택 사항 (Prometheus + Grafana, http://localhost:3000, admin/admin)
docker-compose -f ./docker/monitoring-compose.yml up
```

## 레이어드 아키텍처

각 앱은 4계층 패키지 구조를 엄격히 따른다:

| 패키지 | 역할 |
| --- | --- |
| `interfaces.api` | Controller, DTO, `ApiResponse<T>` 래퍼, `ApiControllerAdvice` |
| `application` | Facade — 도메인 서비스 조합, Info 객체 반환 |
| `domain` | Model, Service (비즈니스 로직), Repository 인터페이스 |
| `infrastructure` | JPA 구현체 (`*JpaRepository`, `*RepositoryImpl`) |

**데이터 흐름:** `Controller → Facade → Service → Repository ← RepositoryImpl → JpaRepository`

- Controller는 Service가 아닌 Facade를 호출한다
- Facade는 도메인 Model을 인터페이스 계층용 Info 객체로 변환한다
- Service는 비즈니스 오류 발생 시 `CoreException(ErrorType)`을 던진다
- `ApiControllerAdvice`가 `CoreException`을 HTTP 응답으로 변환한다

## 테스트 컨벤션

- **단위 테스트**: `domain/*ModelTest` — 순수 JUnit, Spring 컨텍스트 없음
- **통합 테스트**: `domain/*ServiceIntegrationTest` — `@SpringBootTest`, 모듈 testFixtures의 Testcontainers 사용, `@AfterEach`에서 `DatabaseCleanUp.truncateAllTables()` 호출
- **E2E 테스트**: `interfaces/api/*E2ETest` — `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `TestRestTemplate` 사용
- 테스트 패턴: `arrange / act / assert` 구조, 그룹 검증 시 `assertAll` 사용
- Instancio (`instancio-junit`) — 테스트 데이터 생성에 활용 가능
- SpringMockK — Mockito 대신 Kotlin 스타일 모킹에 활용 가능

## 주요 컨벤션

- `BaseEntity` (`:modules:jpa`)가 모든 JPA 엔티티의 기반 클래스
- API 버전은 URL에 포함: `/api/v1/...`; 컨트롤러 명세는 `*ApiSpec` 인터페이스로 정의
- `ErrorType` enum이 모든 에러 코드를 정의; `CoreException`이 도메인 계층에서 던지는 단일 예외 타입
- 개발 완료된 API는 `.http/**.http`에 분류해 작성한다

## 개발 규칙

### 진행 Workflow — 증강 코딩

- **대원칙**: 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행한다.
- **중간 결과 보고**: AI가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현하거나, 테스트를 임의로 삭제할 경우 개발자가 개입한다.
- **설계 주도권 유지**: AI가 임의 판단하지 않고, 방향성에 대한 제안은 할 수 있으나 개발자의 승인을 받은 후 수행한다.
- **md 파일 중복 방지**: `.md` 파일에 내용을 추가하기 전에 동일하거나 유사한 내용이 이미 존재하는지 먼저 확인하고, 중복이 발견되면 개발자에게 알리고 승인을 받은 후 진행한다.
- **개발 완료 후 자동 커밋**: 소스 개발이 완료되어 모든 테스트가 통과하면, 변경된 파일을 스테이징하고 작업 내용을 요약한 커밋 메시지로 자동으로 `git commit`을 수행한다.

### 개발 Workflow — TDD (Red → Green → Refactor)

모든 테스트는 3A 원칙으로 작성한다 (Arrange - Act - Assert).

1. **Red Phase**: 요구사항을 만족하는 실패하는 테스트 케이스 먼저 작성
2. **Green Phase**: Red Phase의 테스트가 모두 통과할 수 있는 코드 작성. 오버엔지니어링 금지.
3. **Refactor Phase**: 불필요한 코드 제거 및 품질 개선. 불필요한 private 함수 지양, unused import 제거, 성능 최적화. 모든 테스트 케이스가 통과해야 함.

## 주의사항

### Never Do

- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용한 구현 금지
- null-safety하지 않은 코드 작성 금지 (Java의 경우 `Optional` 활용)
- `println` 코드 남기지 말 것

### Recommendation

- 실제 API를 호출해 확인하는 E2E 테스트 코드 작성
- 재사용 가능한 객체 설계
- 성능 최적화에 대한 대안 및 제안

### Priority

1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지
