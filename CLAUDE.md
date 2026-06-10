# CLAUDE.md

## 프로젝트
부트캠프 학습용 커머스 도메인 멀티 모듈 템플릿 (Gradle Java DSL).

## 핵심 스택
- Java 21 / Spring Boot 3.4.4 / Spring Cloud 2024.0.1
- JPA + QueryDSL(jakarta), Lombok, SpringDoc 2.7.0
- 테스트: JUnit Platform, Mockito 5.14, springmockk 4.0.2, Instancio 5.0.2, Testcontainers (MySQL)
- 상세 버전: `gradle.properties` / `build.gradle.kts`
- 활성 프로필: 기본 `local`, 테스트 `test` (`Asia/Seoul`)

## 모듈 (`settings.gradle.kts`)
- `apps/{commerce-api, commerce-batch, commerce-streamer}` — 1주차 작업 대상은 `commerce-api`
- `modules/{jpa, redis, kafka}` — 인프라 설정
- `supports/{jackson, logging, monitoring}` — 부가 모듈

## commerce-api 패키지 계층 (`com.loopers`)
- `interfaces.api.<domain>` — Controller / ApiSpec / Dto
- `application.<domain>` — Facade / Info (유스케이스 + 트랜잭션 경계)
- `domain.<domain>` — Model / Service / Repository(IF)
- `infrastructure.<domain>` — JpaRepository / RepositoryImpl
- `support.error` — CoreException / ErrorType

## 공통 컨벤션
- 엔티티는 `BaseEntity` 상속 (id/createdAt/updatedAt/deletedAt 자동).
- 도메인 검증 실패: `CoreException(ErrorType.BAD_REQUEST, ...)`.
- 응답 래핑 `ApiResponse<T>`, 예외 변환 `ApiControllerAdvice`.
- 컨트롤러는 `*V1ApiSpec` implements (Swagger 어노테이션 분리).

## 도메인 & 객체 설계 전략
- 도메인 객체는 데이터가 아닌 **행위와 책임**을 가진다. 비즈니스 규칙은 도메인 안에 캡슐화한다.
- **Entity**: 고유 ID 로 동일성 판단, 상태 변화·연속성을 가짐 (`User`, `Product`, `Order`).
- **Value Object**: 값이 같으면 동일한 불변 객체. 값/계산 중심 개념은 VO 로 표현하고 규칙(음수 방지 등)을 내부에 캡슐화 (`Money`, `Quantity`). JPA 는 `@Embeddable` class 로 매핑 (record 불가).
- **Domain Service**: 상태 없이 여러 도메인 객체의 협력이 필요한 로직만 담당. 단순 위임·연산(doer)은 도메인이 아니다.
- 같은 규칙이 여러 서비스에 중복되면 도메인 객체로 옮길 후보다.
- Aggregate 내부 엔티티는 root 를 통해서만 접근한다 (예: `OrderItem` 은 `Order` 경유).
- 책임·결합도 판단이 모호하면 임의로 정하지 말고 개발자 의도를 먼저 확인한다.

## 아키텍처 & DIP 전략
- 레이어드 아키텍처 + DIP. 의존 방향은 **Application → Domain ← Infrastructure**, 모든 화살표는 Domain 을 향한다.
- Domain 은 다른 계층에 의존하지 않는다. Repository **인터페이스는 domain**, **구현체는 infrastructure** 에 둔다.
- Application(Facade)은 도메인 객체를 조합해 흐름만 orchestration 하고, 비즈니스 로직은 도메인에 위임한다.
- API request/response DTO 와 application 의 Info/Command 는 분리한다.
- 테스트는 외부 의존성을 Fake/Stub 으로 분리해 단위 테스트 가능하게 구성한다.

## 개발 Workflow
**증강 코딩**: AI 는 방향성·결정에 *제안만* 한다. 임의 판단/테스트 삭제 금지.

**TDD (Red → Green → Refactor)**, 모든 테스트는 3A (Arrange-Act-Assert).
1. Red: 실패하는 테스트 먼저.
2. Green: 통과시키는 최소 코드 (오버엔지니어링 금지).
3. Refactor: 테스트 통과 유지하며 정리 (unused import, 응집).

## 커밋 규칙
작업 단위로 분리, 전체 1커밋 금지.
prefix: `feat:` / `fix:` / `refactor:` / `docs:` / `test:`.

## Never Do
- 실제 동작 안 하는 코드 / Mock 데이터로 흉내내기.
- null-safety 누락 (Optional 활용).
- `System.out.println` 잔존 (SLF4J 사용).

## Priority
1. 동작하는 해결책 → 2. null/thread-safety → 3. 테스트 가능한 구조 → 4. 기존 패턴 일관성.

## 권장
- E2E 는 실제 API 호출로 검증.
- 완성 API 는 `apps/commerce-api/.http/*.http` 시나리오 정리.

## 빠른 명령
- 테스트: `./gradlew :apps:commerce-api:test`
- 특정: `./gradlew :apps:commerce-api:test --tests "*UserModelTest"`
- 인프라: `docker-compose -f ./docker/infra-compose.yml up` (상세는 `README.md`)
